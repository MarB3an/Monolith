package edu.cit.pescante.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Enforced package-private implementation of InventoryService.
 * Classes outside edu.cit.pescante.inventory (such as the Order module)
 * CANNOT directly reference or import this class.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryItem> getItem(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return Optional.empty();
        }
        return inventoryRepository.findById(productId.trim());
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        if (productId == null || productId.trim().isEmpty()) {
            return ReservationResult.rejected("Product ID is required", null);
        }

        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than zero", null);
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findById(productId.trim());
        if (optionalItem.isEmpty()) {
            return ReservationResult.rejected("Product '" + productId + "' not found in inventory", null);
        }

        InventoryItem item = optionalItem.get();
        int currentStock = item.getStock() != null ? item.getStock() : 0;

        if (quantity > currentStock) {
            return ReservationResult.rejected(
                    "Requested quantity (" + quantity + ") exceeds available stock (" + currentStock + ")",
                    item
            );
        }

        // Deduct stock and update database
        int newStock = currentStock - quantity;
        item.setStock(newStock);
        InventoryItem savedItem = inventoryRepository.save(item);

        return ReservationResult.success(
                "Order placed successfully (" + quantity + " item(s) reserved)",
                savedItem
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}
