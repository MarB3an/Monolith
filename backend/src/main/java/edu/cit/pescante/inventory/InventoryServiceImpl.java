package edu.cit.pescante.inventory;

import edu.cit.pescante.inventory.events.LowStockEvent;
import edu.cit.pescante.supplier.ReorderResult;
import edu.cit.pescante.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SupplierGateway supplierGateway;
    private final int lowStockThreshold;
    private final int reorderQuantity;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         ApplicationEventPublisher eventPublisher,
                         SupplierGateway supplierGateway,
                         @Value("${inventory.low-stock-threshold:5}") int lowStockThreshold,
                         @Value("${inventory.reorder-quantity:10}") int reorderQuantity) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.supplierGateway = supplierGateway;
        this.lowStockThreshold = lowStockThreshold;
        this.reorderQuantity = reorderQuantity;
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

        // Low-stock auto-reorder rule: publish event and trigger supplier reorder
        if (newStock < lowStockThreshold) {
            log.warn("Product {} stock dropped to {} (threshold: {}). Publishing LowStockEvent and initiating reorder.",
                    productId, newStock, lowStockThreshold);
            eventPublisher.publishEvent(new LowStockEvent(productId, item.getName(), newStock, lowStockThreshold));
            try {
                ReorderResult result = supplierGateway.reorder(productId, reorderQuantity);
                log.info("Auto-reorder initiated for {}: {}", productId, result);
            } catch (Exception e) {
                log.error("Failed to initiate supplier reorder for {}: {}", productId, e.getMessage());
            }
        }

        return ReservationResult.success(
                "Order placed successfully (" + quantity + " item(s) reserved)",
                savedItem
        );
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        if (productId == null || productId.trim().isEmpty() || quantity <= 0) {
            return;
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findById(productId.trim());
        if (optionalItem.isPresent()) {
            InventoryItem item = optionalItem.get();
            int currentStock = item.getStock() != null ? item.getStock() : 0;
            int newStock = currentStock + quantity;
            item.setStock(newStock);
            inventoryRepository.save(item);
            log.info("Restocked product {} with {} units. New stock: {}", productId, quantity, newStock);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}
