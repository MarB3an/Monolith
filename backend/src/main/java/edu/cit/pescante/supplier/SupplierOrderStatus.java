package edu.cit.pescante.supplier;

/**
 * Public domain enum representing the status of a supplier purchase order
 * in our system. Never exposes LegacySupply's numeric status codes.
 *
 * Mapping from LegacySupply StatusCode:
 *   10 (Accepted)  -> ORDERED
 *   20 (Picking)   -> IN_FULFILLMENT
 *   30 (Shipped)   -> IN_FULFILLMENT
 *   40 (Delivered) -> DELIVERED
 *   (unknown)      -> UNKNOWN
 */
public enum SupplierOrderStatus {

    /** Reorder created locally but not yet sent to LegacySupply (awaiting outage recovery). */
    PENDING,

    /** Successfully placed with LegacySupply and acknowledged (StatusCode 10). */
    ORDERED,

    /** LegacySupply is picking or shipping the order (StatusCode 20 or 30). */
    IN_FULFILLMENT,

    /** Delivered — Inventory has been restocked (StatusCode 40). */
    DELIVERED,

    /** Supplier indicated the order was cancelled. */
    CANCELLED,

    /** All retry attempts failed; requires manual intervention. */
    FAILED,

    /** LegacySupply returned a status code not covered by our mapping. */
    UNKNOWN
}
