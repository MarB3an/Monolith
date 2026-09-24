package edu.cit.pescante.supplier;

/**
 * Public domain result returned by {@link SupplierGateway#reorder}.
 * Contains only our domain terms; no LegacySupply concepts leak out.
 */
public class ReorderResult {

    private final String productId;
    private final SupplierOrderStatus status;
    private final String buyerRef;
    private final String poNumber;
    private final int unitsOrdered;
    private final String message;

    private ReorderResult(String productId, SupplierOrderStatus status,
                          String buyerRef, String poNumber, int unitsOrdered, String message) {
        this.productId = productId;
        this.status = status;
        this.buyerRef = buyerRef;
        this.poNumber = poNumber;
        this.unitsOrdered = unitsOrdered;
        this.message = message;
    }

    public static ReorderResult ordered(String productId, String buyerRef,
                                        String poNumber, int unitsOrdered) {
        return new ReorderResult(productId, SupplierOrderStatus.ORDERED,
                buyerRef, poNumber, unitsOrdered, "Reorder placed successfully");
    }

    public static ReorderResult pending(String productId, String buyerRef, int unitsOrdered, String reason) {
        return new ReorderResult(productId, SupplierOrderStatus.PENDING,
                buyerRef, null, unitsOrdered, "Reorder queued as PENDING: " + reason);
    }

    public static ReorderResult failed(String productId, String reason) {
        return new ReorderResult(productId, SupplierOrderStatus.FAILED,
                null, null, 0, "Reorder failed: " + reason);
    }

    public static ReorderResult skipped(String productId) {
        return new ReorderResult(productId, SupplierOrderStatus.PENDING,
                null, null, 0, "Supplier API key not configured; reorder skipped");
    }

    public String getProductId() { return productId; }
    public SupplierOrderStatus getStatus() { return status; }
    public String getBuyerRef() { return buyerRef; }
    public String getPoNumber() { return poNumber; }
    public int getUnitsOrdered() { return unitsOrdered; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "ReorderResult{productId='" + productId + "', status=" + status
                + ", buyerRef='" + buyerRef + "', poNumber='" + poNumber
                + "', units=" + unitsOrdered + ", msg='" + message + "'}";
    }
}
