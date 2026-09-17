package edu.cit.pescante.shop.events;

import java.time.LocalDateTime;

/**
 * Domain event published by Order module when an order is cancelled and its stock is restocked.
 */
public class OrderCancelledEvent {

    private final Long orderId;
    private final String summary;
    private final LocalDateTime timestamp;

    public OrderCancelledEvent(Long orderId, String summary) {
        this.orderId = orderId;
        this.summary = summary;
        this.timestamp = LocalDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getSummary() {
        return summary;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "OrderCancelledEvent{" +
                "orderId=" + orderId +
                ", summary='" + summary + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
