package edu.cit.pescante.shop.events;

import java.time.LocalDateTime;

/**
 * Domain event published by Order module when an order fails stock validation and is rejected.
 */
public class OrderRejectedEvent {

    private final Long orderId;
    private final String reason;
    private final LocalDateTime timestamp;

    public OrderRejectedEvent(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "OrderRejectedEvent{" +
                "orderId=" + orderId +
                ", reason='" + reason + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
