package edu.cit.pescante.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Package-private HTTP client for the LegacySupply REST API.
 *
 * Responsibilities:
 *  - Build and send XML requests to LegacySupply endpoints.
 *  - Handle session header injection.
 *  - Detect auth errors (E-AUTH-03, E-AUTH-07) for the caller to retry.
 *  - Apply a 3-second read timeout on every call.
 *
 * Does NOT implement retry logic — that lives in {@link SupplierGatewayImpl}.
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);

    /** HTTP/transport timeout per request. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    /** LegacySupply session header name. */
    private static final String SESSION_HEADER = "X-LS-Session";

    /** Idempotency header name. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final String baseUrl;
    private final HttpClient httpClient;

    LegacySupplyClient(@Value("${legacysupply.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    // -------------------------------------------------------------------------
    // Place a purchase order
    // -------------------------------------------------------------------------

    /**
     * POST /purchase-orders
     *
     * @param token      current session token
     * @param sku        LegacySupply supplier SKU
     * @param cases      quantity in cases (whole number 1-99)
     * @param buyerRef   our reference ("RO-{id}")
     * @param requestId  idempotency key ("REQ-RO-{id}")
     * @return raw XML response body
     * @throws LegacySupplyException on network error or non-retryable HTTP error
     */
    LsResponse placeOrder(String token, String sku, int cases,
                          String buyerRef, String requestId) throws LegacySupplyException {
        String body = "<PurchaseOrder>"
                + "<SupplierSku>" + sku + "</SupplierSku>"
                + "<Qty>" + cases + "</Qty>"
                + "<Uom>CS</Uom>"
                + "<BuyerRef>" + buyerRef + "</BuyerRef>"
                + "</PurchaseOrder>";

        log.debug("[LS-CLIENT] POST /purchase-orders  buyerRef={} sku={} qty={}", buyerRef, sku, cases);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase-orders"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/xml")
                .header(SESSION_HEADER, token)
                .header(REQUEST_ID_HEADER, requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return send(request);
    }

    // -------------------------------------------------------------------------
    // Get order status by PoNumber
    // -------------------------------------------------------------------------

    /**
     * GET /purchase-orders/{poNumber}
     */
    LsResponse getOrderStatus(String token, String poNumber) throws LegacySupplyException {
        log.debug("[LS-CLIENT] GET /purchase-orders/{}", poNumber);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase-orders/" + poNumber))
                .timeout(REQUEST_TIMEOUT)
                .header(SESSION_HEADER, token)
                .GET()
                .build();

        return send(request);
    }

    // -------------------------------------------------------------------------
    // Find order by buyerRef (fallback if poNumber is unknown)
    // -------------------------------------------------------------------------

    /**
     * GET /purchase-orders?buyerRef={buyerRef}
     */
    LsResponse findOrderByBuyerRef(String token, String buyerRef) throws LegacySupplyException {
        log.debug("[LS-CLIENT] GET /purchase-orders?buyerRef={}", buyerRef);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase-orders?buyerRef=" + buyerRef))
                .timeout(REQUEST_TIMEOUT)
                .header(SESSION_HEADER, token)
                .GET()
                .build();

        return send(request);
    }

    // -------------------------------------------------------------------------
    // Internal send helper
    // -------------------------------------------------------------------------

    private LsResponse send(HttpRequest request) throws LegacySupplyException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("[LS-CLIENT] <- HTTP {} {}", response.statusCode(),
                    response.body() == null ? "" : response.body().substring(0, Math.min(120, response.body().length())));
            return new LsResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new LegacySupplyException("Network error calling LegacySupply: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Interrupted while calling LegacySupply", e);
        }
    }

    // -------------------------------------------------------------------------
    // Simple response wrapper
    // -------------------------------------------------------------------------

    /** Package-private wrapper for an HTTP response from LegacySupply. */
    static final class LsResponse {
        final int status;
        final String body;

        LsResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        boolean isSuccess() { return status >= 200 && status < 300; }

        /** True if LegacySupply returned a session-related 401 error. */
        boolean isSessionError() {
            if (status != 401) return false;
            String code = LsXmlParser.extractErrorCode(body);
            return "E-AUTH-03".equals(code) || "E-AUTH-07".equals(code);
        }

        /** True if this is a transient server-side error (retry-able). */
        boolean isTransient() {
            return status == 503 || status == 429;
        }

        /** True if this is an idempotent replay (original order already exists). */
        boolean isIdempotentReplay() {
            return status == 200 || (status == 409 && LsXmlParser.hasErrorCode(body, "E-IDEM-04"));
        }

        String errorCode() { return LsXmlParser.extractErrorCode(body); }
    }
}
