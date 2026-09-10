package edu.cit.pescante.shop;

import edu.cit.pescante.inventory.InventoryItem;
import edu.cit.pescante.inventory.InventoryService;
import edu.cit.pescante.inventory.ReservationResult;
import edu.cit.pescante.shop.dto.CreateOrderRequest;
import edu.cit.pescante.shop.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * OrderService in edu.cit.pescante.shop.
 * Demonstrates module-to-module in-process integration with an enforced boundary:
 * it depends ONLY on the public InventoryService interface injected via constructor.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        String productId = request.getProductId() != null ? request.getProductId().trim() : "";
        int quantity = request.getQuantity() != null ? request.getQuantity() : 0;

        // In-process integration call across module boundary
        ReservationResult reservation = inventoryService.reserve(productId, quantity);

        String status;
        String reason;
        InventoryItem inventory = reservation.getItem();

        if (reservation.isSuccess()) {
            status = "CONFIRMED";
            reason = reservation.getReason();
        } else {
            status = "REJECTED";
            reason = reservation.getReason();
            // If item was found but rejected due to stock, reservation.getItem() provides the current stock
            if (inventory == null) {
                inventory = inventoryService.getItem(productId).orElse(null);
            }
        }

        // Write order audit log to orders table
        OrderRecord orderRecord = new OrderRecord(productId, quantity, status, reason);
        OrderRecord savedOrder = orderRepository.save(orderRecord);

        return new OrderResponse(
                status,
                reason,
                inventory,
                savedOrder.getOrderId(),
                savedOrder.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<OrderRecord> getOrderHistory() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
