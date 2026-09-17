package edu.cit.pescante.shop;

import edu.cit.pescante.inventory.InventoryItem;
import edu.cit.pescante.inventory.InventoryService;
import edu.cit.pescante.shop.dto.CreateOrderRequest;
import edu.cit.pescante.shop.dto.OrderItemOutcome;
import edu.cit.pescante.shop.dto.OrderItemRequest;
import edu.cit.pescante.shop.dto.OrderResponse;
import edu.cit.pescante.shop.events.OrderCancelledEvent;
import edu.cit.pescante.shop.events.OrderPlacedEvent;
import edu.cit.pescante.shop.events.OrderRejectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OrderService in edu.cit.pescante.shop.
 * Demonstrates module-to-module in-process integration with an enforced boundary:
 * it depends ONLY on the public InventoryService interface injected via constructor,
 * and publishes domain events (OrderPlaced, OrderRejected, OrderCancelled).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                        OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Places a multi-item order with all-or-nothing transactional semantics.
     * All items are validated against current stock before any reservation is made.
     * If any single item fails, the whole order is REJECTED and 0 items are reserved.
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        List<OrderItemRequest> requestedItems = request.getItems();

        if (requestedItems == null || requestedItems.isEmpty()) {
            OrderRecord emptyOrder = new OrderRecord("REJECTED", "Order must contain at least one line item");
            OrderRecord saved = orderRepository.save(emptyOrder);
            eventPublisher.publishEvent(new OrderRejectedEvent(saved.getOrderId(), emptyOrder.getReason()));
            return new OrderResponse(
                    "REJECTED",
                    emptyOrder.getReason(),
                    List.of(),
                    inventoryService.getAllItems(),
                    saved.getOrderId(),
                    saved.getCreatedAt()
            );
        }

        // Aggregate requested quantities per product (handles duplicate lines in cart)
        Map<String, Integer> totalRequested = new HashMap<>();
        for (OrderItemRequest item : requestedItems) {
            String pid = item.getProductId() != null ? item.getProductId().trim() : "";
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            totalRequested.put(pid, totalRequested.getOrDefault(pid, 0) + qty);
        }

        // --- STEP 1: Pre-validate ALL line items against current stock ---
        boolean allValid = true;
        String rejectReason = null;
        Map<String, String> itemStatuses = new HashMap<>();

        for (Map.Entry<String, Integer> entry : totalRequested.entrySet()) {
            String pid = entry.getKey();
            int qty = entry.getValue();

            if (pid.isEmpty() || qty <= 0) {
                allValid = false;
                rejectReason = "Invalid product ID or quantity";
                itemStatuses.put(pid, "INVALID");
                continue;
            }

            Optional<InventoryItem> itemOpt = inventoryService.getItem(pid);
            if (itemOpt.isEmpty()) {
                allValid = false;
                rejectReason = "Product '" + pid + "' not found in inventory";
                itemStatuses.put(pid, "PRODUCT_NOT_FOUND");
            } else {
                InventoryItem item = itemOpt.get();
                int currentStock = item.getStock() != null ? item.getStock() : 0;
                if (qty > currentStock) {
                    allValid = false;
                    rejectReason = "Requested quantity (" + qty + ") exceeds available stock (" + currentStock + ") for product " + pid;
                    itemStatuses.put(pid, "EXCEEDS_STOCK");
                } else {
                    itemStatuses.put(pid, "IN_STOCK");
                }
            }
        }

        // --- STEP 2: All-or-Nothing Decision ---
        List<OrderItemOutcome> outcomes = new ArrayList<>();
        OrderRecord orderRecord = new OrderRecord();

        if (!allValid) {
            // REJECTED PATH: Do NOT call inventoryService.reserve()! No items reserved.
            orderRecord.setStatus("REJECTED");
            orderRecord.setReason(rejectReason);

            for (OrderItemRequest req : requestedItems) {
                String pid = req.getProductId();
                String status = itemStatuses.getOrDefault(pid, "UNKNOWN");
                String outcome = "EXCEEDS_STOCK".equals(status) ? "EXCEEDS_STOCK" :
                        ("PRODUCT_NOT_FOUND".equals(status) ? "PRODUCT_NOT_FOUND" : "ROLLBACK_UNFULFILLED");
                outcomes.add(new OrderItemOutcome(pid, req.getQuantity(), outcome));
                orderRecord.addItem(new OrderItemRecord(pid, req.getQuantity()));
            }

            OrderRecord savedOrder = orderRepository.save(orderRecord);
            log.info("Order #{} REJECTED: {}", savedOrder.getOrderId(), rejectReason);

            // Publish domain event for Notification module
            eventPublisher.publishEvent(new OrderRejectedEvent(savedOrder.getOrderId(), rejectReason));

            return new OrderResponse(
                    "REJECTED",
                    rejectReason,
                    outcomes,
                    inventoryService.getAllItems(),
                    savedOrder.getOrderId(),
                    savedOrder.getCreatedAt()
            );
        }

        // CONFIRMED PATH: All items passed pre-validation, reserve each item
        for (OrderItemRequest req : requestedItems) {
            inventoryService.reserve(req.getProductId().trim(), req.getQuantity());
            outcomes.add(new OrderItemOutcome(req.getProductId().trim(), req.getQuantity(), "RESERVED"));
            orderRecord.addItem(new OrderItemRecord(req.getProductId().trim(), req.getQuantity()));
        }

        String confirmReason = "Order placed successfully (" + requestedItems.size() + " item(s) reserved)";
        orderRecord.setStatus("CONFIRMED");
        orderRecord.setReason(confirmReason);

        OrderRecord savedOrder = orderRepository.save(orderRecord);
        log.info("Order #{} CONFIRMED with {} line item(s)", savedOrder.getOrderId(), requestedItems.size());

        // Publish domain event for Notification module
        eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder.getOrderId(), confirmReason));

        return new OrderResponse(
                "CONFIRMED",
                confirmReason,
                outcomes,
                inventoryService.getAllItems(),
                savedOrder.getOrderId(),
                savedOrder.getCreatedAt()
        );
    }

    /**
     * Cancels a confirmed order and restocks all reserved line items.
     * Rejects with 404 if not found; rejects with 409 if already CANCELLED.
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        OrderRecord order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order #" + orderId + " not found"));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order #" + orderId + " is already CANCELLED");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot cancel order #" + orderId + " with status '" + order.getStatus() + "'");
        }

        // Restock every reserved line item via InventoryService
        List<OrderItemOutcome> outcomes = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItemRecord item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
                outcomes.add(new OrderItemOutcome(item.getProductId(), item.getQuantity(), "RESTOCKED"));
            }
        }

        order.setStatus("CANCELLED");
        order.setReason("Order cancelled by user - all line items restocked to inventory");
        OrderRecord saved = orderRepository.save(order);
        log.info("Order #{} CANCELLED. Line items restocked.", orderId);

        // Publish domain event for Notification module
        eventPublisher.publishEvent(new OrderCancelledEvent(saved.getOrderId(), saved.getReason()));

        return new OrderResponse(
                "CANCELLED",
                saved.getReason(),
                outcomes,
                inventoryService.getAllItems(),
                saved.getOrderId(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<OrderRecord> getOrderHistory() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
