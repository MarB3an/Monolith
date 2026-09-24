package edu.cit.pescante.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Package-private implementation of {@link SupplierGateway}.
 *
 * Implements the anti-corruption layer:
 *  - Translates productId → supplierSku using {@link ProductSupplierCatalog}
 *  - Converts unitsNeeded → cases (rounded up by packSize)
 *  - Persists a {@link SupplierOrderRecord} before the first attempt (for retry durability)
 *  - Calls {@link LegacySupplyClient} with up to 3 retries and exponential back-off
 *  - On session errors (E-AUTH-03/07), refreshes the token and retries once
 */
@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(SupplierGatewayImpl.class);

    /** Maximum delivery attempts before giving up on this call (PENDING scheduler retries separately). */
    private static final int MAX_ATTEMPTS = 3;

    /** Base delay in ms between retries (doubles each attempt). */
    private static final long BASE_BACKOFF_MS = 1_000;

    private final String apiKey;
    private final LegacySupplyClient client;
    private final LegacySupplySessionManager sessionManager;
    private final SupplierOrderRepository repository;

    SupplierGatewayImpl(
            @Value("${legacysupply.api-key:}") String apiKey,
            LegacySupplyClient client,
            LegacySupplySessionManager sessionManager,
            SupplierOrderRepository repository) {
        this.apiKey = apiKey;
        this.client = client;
        this.sessionManager = sessionManager;
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ReorderResult reorder(String productId, int unitsNeeded) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SUPPLIER] LS_API_KEY not configured — skipping reorder for {}", productId);
            return ReorderResult.skipped(productId);
        }

        Optional<ProductSupplierCatalog.CatalogEntry> entryOpt = ProductSupplierCatalog.forProduct(productId);
        if (entryOpt.isEmpty()) {
            log.warn("[SUPPLIER] No supplier catalog entry for productId={}; skipping reorder", productId);
            return ReorderResult.failed(productId, "No supplier SKU mapped for " + productId);
        }

        ProductSupplierCatalog.CatalogEntry entry = entryOpt.get();
        int cases = entry.unitsToCases(Math.max(1, unitsNeeded));
        int totalUnits = entry.casesToUnits(cases);

        // ---- Step 1: Persist a PENDING record BEFORE the first network call ----
        SupplierOrderRecord record = new SupplierOrderRecord();
        record.setProductId(productId);
        record.setStatus(SupplierOrderStatus.PENDING);
        record.setCases(cases);
        record.setUnits(totalUnits);
        record = repository.save(record); // generates the ID

        // ---- Step 2: Set idempotency keys based on the DB-generated ID ----
        record.setBuyerRef("RO-" + record.getId());
        record.setRequestId("REQ-RO-" + record.getId());
        record = repository.save(record);

        log.info("[SUPPLIER] Initiating reorder: productId={} sku={} cases={} units={} buyerRef={}",
                productId, entry.supplierSku, cases, totalUnits, record.getBuyerRef());

        // ---- Step 3: Attempt to place the order with retries ----
        return attemptPlaceOrder(record, entry.supplierSku);
    }

    // -------------------------------------------------------------------------
    // Internal — place with retry
    // -------------------------------------------------------------------------

    /**
     * Calls LegacySupply to place the purchase order.
     * Retries up to MAX_ATTEMPTS with exponential back-off.
     * On session errors, immediately re-authenticates and retries.
     * Leaves the record in PENDING state on failure so the scheduler can retry.
     */
    @Transactional
    ReorderResult attemptPlaceOrder(SupplierOrderRecord record, String sku) {
        String buyerRef = record.getBuyerRef();
        String requestId = record.getRequestId();
        int cases = record.getCases();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String token = sessionManager.getToken();
                LegacySupplyClient.LsResponse response = client.placeOrder(token, sku, cases, buyerRef, requestId);

                // Successful acknowledgement (201 = new, 200 = idempotent replay)
                if (response.status == 201 || (response.status == 200)) {
                    String poNumber = LsXmlParser.extractTag(response.body, "PoNumber");
                    record.setPoNumber(poNumber);
                    record.setStatus(SupplierOrderStatus.ORDERED);
                    repository.save(record);
                    log.info("[SUPPLIER] Order placed  buyerRef={} poNumber={} units={}",
                            buyerRef, poNumber, record.getUnits());
                    return ReorderResult.ordered(record.getProductId(), buyerRef, poNumber, record.getUnits());
                }

                // Session expired — refresh and retry immediately (counts as one attempt)
                if (response.isSessionError()) {
                    log.warn("[SUPPLIER] Session error on attempt {}; re-authenticating", attempt);
                    sessionManager.invalidateAndRefresh();
                    // don't count this as a full attempt — loop again
                    attempt--;
                    continue;
                }

                // Non-retryable client errors
                if (response.status == 409 && "E-IDEM-04".equals(response.errorCode())) {
                    // Same requestId but different content — something is very wrong
                    log.error("[SUPPLIER] Idempotency conflict on buyerRef={}; manual review needed", buyerRef);
                    record.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(record);
                    return ReorderResult.failed(record.getProductId(), "Idempotency conflict E-IDEM-04");
                }

                if (response.status == 422) {
                    log.error("[SUPPLIER] Unprocessable order ({}): {} — not retrying", response.errorCode(), response.body);
                    record.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(record);
                    return ReorderResult.failed(record.getProductId(), "Invalid order: " + response.errorCode());
                }

                // Transient errors (503, 429) — back off and retry
                if (response.isTransient()) {
                    log.warn("[SUPPLIER] Transient error {} on attempt {}/{}; backing off",
                            response.status, attempt, MAX_ATTEMPTS);
                } else {
                    log.warn("[SUPPLIER] Unexpected HTTP {} ({}) on attempt {}/{}",
                            response.status, response.errorCode(), attempt, MAX_ATTEMPTS);
                }

            } catch (LegacySupplyException e) {
                log.warn("[SUPPLIER] Network error on attempt {}/{}: {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }

            // Back-off before next attempt (skip after last attempt)
            if (attempt < MAX_ATTEMPTS) {
                long delay = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 1s, 2s
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        // All attempts exhausted — leave as PENDING for the scheduler to retry later
        log.warn("[SUPPLIER] All {} attempts failed for buyerRef={}; leaving PENDING for scheduler",
                MAX_ATTEMPTS, buyerRef);
        // Status remains PENDING (already persisted)
        return ReorderResult.pending(record.getProductId(), buyerRef, record.getUnits(),
                "All " + MAX_ATTEMPTS + " placement attempts failed; scheduler will retry");
    }
}
