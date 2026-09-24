package edu.cit.pescante.supplier;

/**
 * Public gateway interface for the supplier module.
 * The only entry point that external modules (Inventory) may call.
 * Accepts domain terms only: productId and units needed.
 */
public interface SupplierGateway {

    /**
     * Places a replenishment reorder for the given product.
     *
     * @param productId   our domain product ID (e.g. "P100")
     * @param unitsNeeded how many individual units to restock
     * @return the outcome of the reorder attempt (never null)
     */
    ReorderResult reorder(String productId, int unitsNeeded);
}
