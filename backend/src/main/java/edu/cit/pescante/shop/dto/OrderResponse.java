package edu.cit.pescante.shop.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class OrderResponse {

    private String status;
    private String reason;
    private List<OrderItemOutcome> items = new ArrayList<>();
    private Object inventory;
    private Long orderId;
    private LocalDateTime createdAt;

    public OrderResponse() {
    }

    public OrderResponse(String status, String reason, Object inventory) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
    }

    public OrderResponse(String status, String reason, Object inventory, Long orderId, LocalDateTime createdAt) {
        this.status = status;
        this.reason = reason;
        this.inventory = inventory;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public OrderResponse(String status, String reason, List<OrderItemOutcome> items, Object inventory, Long orderId, LocalDateTime createdAt) {
        this.status = status;
        this.reason = reason;
        this.items = items != null ? items : new ArrayList<>();
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

    public List<OrderItemOutcome> getItems() {
        return items;
    }

    public void setItems(List<OrderItemOutcome> items) {
        this.items = items;
    }

    public Object getInventory() {
        return inventory;
    }

    public void setInventory(Object inventory) {
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
