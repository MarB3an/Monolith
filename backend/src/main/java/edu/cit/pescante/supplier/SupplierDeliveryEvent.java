package edu.cit.pescante.supplier;

/**
 * Public domain event published when a supplier order reaches the DELIVERED state.
 * Inventory listens to this to restock units. Contains only our domain vocabulary.
 */
public class SupplierDeliveryEvent {

    private final String productId;
    private final int units;
    private final String buyerRef;
    private final long supplierOrderId;

    public SupplierDeliveryEvent(long supplierOrderId, String productId, int units, String buyerRef) {
        this.supplierOrderId = supplierOrderId;
        this.productId = productId;
        this.units = units;
        this.buyerRef = buyerRef;
    }

    /** Our product identifier (e.g. "P100"). */
    public String getProductId() { return productId; }

    /** Number of individual units delivered. */
    public int getUnits() { return units; }

    /** The buyer reference used when placing the order (e.g. "RO-42"). */
    public String getBuyerRef() { return buyerRef; }

    /** Primary key of the supplier_orders row. */
    public long getSupplierOrderId() { return supplierOrderId; }

    @Override
    public String toString() {
        return "SupplierDeliveryEvent{supplierOrderId=" + supplierOrderId
                + ", productId='" + productId + "', units=" + units
                + ", buyerRef='" + buyerRef + "'}";
    }
}
