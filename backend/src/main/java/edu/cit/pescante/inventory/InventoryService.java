package edu.cit.pescante.inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryService {

    /**
     * Retrieves an inventory item by productId.
     */
    Optional<InventoryItem> getItem(String productId);

    /**
     * Reserves requested quantity of a product.
     * Updates inventory table; rejects if requested quantity exceeds stock.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Lists all inventory items (useful for populating frontend dropdown).
     */
    List<InventoryItem> getAllItems();

    /**
     * Returns reserved quantity of a product back to stock upon order cancellation.
     */
    void restock(String productId, int quantity);
}
