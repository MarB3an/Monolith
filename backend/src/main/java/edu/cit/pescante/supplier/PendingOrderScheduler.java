package edu.cit.pescante.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Package-private scheduled job that retries PENDING supplier orders.
 *
 * A reorder enters PENDING state when:
 *  - LegacySupply was unavailable (503/network) during all immediate attempts, OR
 *  - The app restarted before the order was confirmed.
 *
 * Every 30 seconds this job loads all PENDING rows and calls the gateway's
 * placement logic again (same requestId and buyerRef for idempotency).
 */
@Component
class PendingOrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderScheduler.class);

    private final String apiKey;
    private final SupplierOrderRepository repository;
    private final SupplierGatewayImpl gatewayImpl;
    private final LegacySupplySessionManager sessionManager;
    private final LegacySupplyClient client;

    PendingOrderScheduler(
            @Value("${legacysupply.api-key:}") String apiKey,
            SupplierOrderRepository repository,
            SupplierGatewayImpl gatewayImpl,
            LegacySupplySessionManager sessionManager,
            LegacySupplyClient client) {
        this.apiKey = apiKey;
        this.repository = repository;
        this.gatewayImpl = gatewayImpl;
        this.sessionManager = sessionManager;
        this.client = client;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    @Transactional
    public void retryPendingOrders() {
        if (apiKey == null || apiKey.isBlank()) return;

        List<SupplierOrderRecord> pending = repository.findByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) return;

        log.info("[PENDING-SCHEDULER] {} PENDING order(s) to retry", pending.size());

        for (SupplierOrderRecord record : pending) {
            // Skip records with no requestId (race condition during initial insert — very rare)
            if (record.getRequestId() == null) continue;

            Optional<ProductSupplierCatalog.CatalogEntry> entryOpt =
                    ProductSupplierCatalog.forProduct(record.getProductId());

            if (entryOpt.isEmpty()) {
                log.warn("[PENDING-SCHEDULER] No catalog entry for productId={}; marking FAILED",
                        record.getProductId());
                record.setStatus(SupplierOrderStatus.FAILED);
                repository.save(record);
                continue;
            }

            log.info("[PENDING-SCHEDULER] Retrying buyerRef={} productId={}",
                    record.getBuyerRef(), record.getProductId());
            gatewayImpl.attemptPlaceOrder(record, entryOpt.get().supplierSku);
        }
    }
}
