package edu.cit.pescante.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.cit.pescante.inventory.InventoryItem;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class OrderResponse {

    private String status;
    private String reason;
    private InventoryItem inventory;
    private Long orderId;
    private LocalDateTime createdAt;

    public OrderResponse() {
    }

    public OrderResponse(String status, String reason, InventoryItem inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public OrderResponse(String status, String reason, InventoryItem inventory, Long orderId, LocalDateTime createdAt) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public InventoryItem getInventory() {
        return inventory;
    }

    public void setInventory(InventoryItem inventory) {
        this.inventory = inventory;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
