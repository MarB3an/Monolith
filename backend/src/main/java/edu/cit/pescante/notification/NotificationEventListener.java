package edu.cit.pescante.notification;

import edu.cit.pescante.inventory.events.LowStockEvent;
import edu.cit.pescante.shop.events.OrderCancelledEvent;
import edu.cit.pescante.shop.events.OrderPlacedEvent;
import edu.cit.pescante.shop.events.OrderRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener in edu.cit.pescante.notification.
 * Strictly adheres to architectural boundary rules:
 * - Depends ONLY on domain event classes (OrderPlacedEvent, OrderRejectedEvent, OrderCancelledEvent, LowStockEvent)
 * - NEVER imports or invokes OrderService, OrderRepository, InventoryService, or InventoryRepository.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationRepository notificationRepository;

    public NotificationEventListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    public void handleOrderPlaced(OrderPlacedEvent event) {
        String msg = "Order #" + event.getOrderId() + " confirmed";
        log.info("Notification received: {}", msg);
        notificationRepository.save(new NotificationRecord(msg));
    }

    @EventListener
    public void handleOrderRejected(OrderRejectedEvent event) {
        String msg = "Order #" + event.getOrderId() + " rejected: " + event.getReason();
        log.info("Notification received: {}", msg);
        notificationRepository.save(new NotificationRecord(msg));
    }

    @EventListener
    public void handleLowStock(LowStockEvent event) {
        String msg = "Low stock alert: Product " + event.getProductId() + " (" + event.getProductName() +
                ") stock is " + event.getRemainingStock() + " (threshold: " + event.getThreshold() + ") - reorder needed";
        log.info("Notification received: {}", msg);
        notificationRepository.save(new NotificationRecord(msg));
    }

    @EventListener
    public void handleOrderCancelled(OrderCancelledEvent event) {
        String msg = "Order #" + event.getOrderId() + " cancelled - line items restocked to inventory";
        log.info("Notification received: {}", msg);
        notificationRepository.save(new NotificationRecord(msg));
    }
}
