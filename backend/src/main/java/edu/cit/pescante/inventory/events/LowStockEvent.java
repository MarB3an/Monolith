package edu.cit.pescante.inventory.events;

/**
 * Domain event published by Inventory module when an item's remaining stock
 * drops below the configured low-stock threshold (e.g., 5).
 */
public class LowStockEvent {

    private final String productId;
    private final String productName;
    private final int remainingStock;
    private final int threshold;

    public LowStockEvent(String productId, String productName, int remainingStock, int threshold) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
        this.threshold = threshold;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }

    public int getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return "LowStockEvent{" +
                "productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", remainingStock=" + remainingStock +
                ", threshold=" + threshold +
                '}';
    }
}
