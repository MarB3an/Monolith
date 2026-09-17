package edu.cit.pescante.shop.dto;

public class OrderItemOutcome {

    private String productId;
    private Integer quantity;
    private String outcome; // e.g. "RESERVED", "EXCEEDS_STOCK", "OUT_OF_STOCK", "PRODUCT_NOT_FOUND", "ROLLBACK_UNFULFILLED"

    public OrderItemOutcome() {
    }

    public OrderItemOutcome(String productId, String outcome) {
        this.productId = productId;
        this.outcome = outcome;
    }

    public OrderItemOutcome(String productId, Integer quantity, String outcome) {
        this.productId = productId;
        this.quantity = quantity;
        this.outcome = outcome;
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

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    @Override
    public String toString() {
        return "OrderItemOutcome{" +
                "productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", outcome='" + outcome + '\'' +
                '}';
    }
}
