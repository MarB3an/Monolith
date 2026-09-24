package edu.cit.pescante.inventory;

import edu.cit.pescante.supplier.SupplierDeliveryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Package-private event listener that restocks inventory when a supplier delivery arrives.
 *
 * Listens to {@link SupplierDeliveryEvent} (our domain event — no LegacySupply terms).
 * Calls {@link InventoryService#restock} with the productId and units delivered.
 */
@Component
class InventorySupplierListener {

    private static final Logger log = LoggerFactory.getLogger(InventorySupplierListener.class);

    private final InventoryService inventoryService;

    InventorySupplierListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierDelivery(SupplierDeliveryEvent event) {
        log.info("[INVENTORY] Received SupplierDeliveryEvent: productId={} units={} buyerRef={}",
                event.getProductId(), event.getUnits(), event.getBuyerRef());
        inventoryService.restock(event.getProductId(), event.getUnits());
        log.info("[INVENTORY] Restocked {} units of product {}", event.getUnits(), event.getProductId());
    }
}
