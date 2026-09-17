package edu.cit.pescante.shop.events;

import java.time.LocalDateTime;

/**
 * Domain event published by Order module when an order is successfully validated and placed.
 */
public class OrderPlacedEvent {

    private final Long orderId;
    private final String summary;
    private final LocalDateTime timestamp;

    public OrderPlacedEvent(Long orderId, String summary) {
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
        return "OrderPlacedEvent{" +
                "orderId=" + orderId +
                ", summary='" + summary + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
