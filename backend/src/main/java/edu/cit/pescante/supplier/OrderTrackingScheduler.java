package edu.cit.pescante.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Package-private scheduled job that polls LegacySupply for the status of
 * open supplier orders (ORDERED or IN_FULFILLMENT).
 *
 * <p>Polls every 30 seconds. When an order reaches StatusCode 40 (Delivered),
 * it:
 * <ol>
 *   <li>Updates the {@link SupplierOrderRecord} to {@link SupplierOrderStatus#DELIVERED}.</li>
 *   <li>Publishes a {@link SupplierDeliveryEvent} so the Inventory module can restock.</li>
 * </ol>
 *
 * <p>Status code mapping (LegacySupply → our domain):
 * <ul>
 *   <li>10 (Accepted)  → {@link SupplierOrderStatus#ORDERED}</li>
 *   <li>20 (Picking)   → {@link SupplierOrderStatus#IN_FULFILLMENT}</li>
 *   <li>30 (Shipped)   → {@link SupplierOrderStatus#IN_FULFILLMENT}</li>
 *   <li>40 (Delivered) → {@link SupplierOrderStatus#DELIVERED}</li>
 *   <li>(other)        → {@link SupplierOrderStatus#UNKNOWN} (logged, not restocked)</li>
 * </ul>
 */
@Component
class OrderTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTrackingScheduler.class);

    private final String apiKey;
    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final LegacySupplySessionManager sessionManager;
    private final ApplicationEventPublisher eventPublisher;

    OrderTrackingScheduler(
            @Value("${legacysupply.api-key:}") String apiKey,
            SupplierOrderRepository repository,
            LegacySupplyClient client,
            LegacySupplySessionManager sessionManager,
            ApplicationEventPublisher eventPublisher) {
        this.apiKey = apiKey;
        this.repository = repository;
        this.client = client;
        this.sessionManager = sessionManager;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 90_000)
    @Transactional
    public void pollOpenOrders() {
        if (apiKey == null || apiKey.isBlank()) return;

        List<SupplierOrderRecord> openOrders = repository.findByStatusIn(
                List.of(SupplierOrderStatus.ORDERED, SupplierOrderStatus.IN_FULFILLMENT));

        if (openOrders.isEmpty()) return;

        log.info("[TRACKING-SCHEDULER] Polling status for {} open order(s)", openOrders.size());

        for (SupplierOrderRecord record : openOrders) {
            pollSingleOrder(record);
        }
    }

    private void pollSingleOrder(SupplierOrderRecord record) {
        try {
            String token = sessionManager.getToken();
            LegacySupplyClient.LsResponse response;

            if (record.getPoNumber() != null) {
                response = client.getOrderStatus(token, record.getPoNumber());
            } else {
                // Fallback: query by buyerRef if we somehow lost the PoNumber
                response = client.findOrderByBuyerRef(token, record.getBuyerRef());
            }

            if (response.isSessionError()) {
                log.warn("[TRACKING-SCHEDULER] Session error for buyerRef={}; will retry next cycle",
                        record.getBuyerRef());
                sessionManager.invalidateAndRefresh();
                return;
            }

            if (!response.isSuccess()) {
                log.warn("[TRACKING-SCHEDULER] Error {} ({}) polling buyerRef={}",
                        response.status, response.errorCode(), record.getBuyerRef());
                return;
            }

            int statusCode = LsXmlParser.extractStatusCode(response.body);
            SupplierOrderStatus newStatus = mapStatusCode(statusCode);

            if (newStatus == record.getStatus()) return; // no change

            log.info("[TRACKING-SCHEDULER] Order {} status: {} -> {} (LS code {})",
                    record.getBuyerRef(), record.getStatus(), newStatus, statusCode);

            record.setStatus(newStatus);
            repository.save(record);

            if (newStatus == SupplierOrderStatus.DELIVERED) {
                SupplierDeliveryEvent event = new SupplierDeliveryEvent(
                        record.getId(), record.getProductId(), record.getUnits(), record.getBuyerRef());
                eventPublisher.publishEvent(event);
                log.info("[TRACKING-SCHEDULER] Published SupplierDeliveryEvent for productId={} units={}",
                        record.getProductId(), record.getUnits());
            }

            if (newStatus == SupplierOrderStatus.UNKNOWN) {
                log.warn("[TRACKING-SCHEDULER] Unknown status code {} for buyerRef={}; manual review required",
                        statusCode, record.getBuyerRef());
            }

        } catch (LegacySupplyException e) {
            log.warn("[TRACKING-SCHEDULER] Network error polling buyerRef={}: {}",
                    record.getBuyerRef(), e.getMessage());
        }
    }

    /**
     * Maps a LegacySupply numeric status code to our domain enum.
     * Unknown codes return UNKNOWN and are logged by the caller.
     *
     * StatusCode 10 = Accepted  → ORDERED
     * StatusCode 20 = Picking   → IN_FULFILLMENT
     * StatusCode 30 = Shipped   → IN_FULFILLMENT
     * StatusCode 40 = Delivered → DELIVERED
     */
    private SupplierOrderStatus mapStatusCode(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ORDERED;
            case 20, 30 -> SupplierOrderStatus.IN_FULFILLMENT;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }
}
