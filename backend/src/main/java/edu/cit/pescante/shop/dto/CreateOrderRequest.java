package edu.cit.pescante.shop.dto;

import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {

    private List<@Valid OrderItemRequest> items;

    // Optional fields for backwards compatibility with single-item orders
    private String productId;
    private Integer quantity;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(List<OrderItemRequest> items) {
        this.items = items;
    }

    public CreateOrderRequest(String productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.items = new ArrayList<>(List.of(new OrderItemRequest(productId, quantity)));
    }

    public List<OrderItemRequest> getItems() {
        if (items != null && !items.isEmpty()) {
            return items;
        }
        if (productId != null && !productId.trim().isEmpty() && quantity != null && quantity > 0) {
            return List.of(new OrderItemRequest(productId.trim(), quantity));
        }
        return items != null ? items : new ArrayList<>();
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest{" +
                "items=" + getItems() +
                '}';
    }
}
