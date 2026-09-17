package edu.cit.pescante.shop;

import edu.cit.pescante.shop.dto.CreateOrderRequest;
import edu.cit.pescante.shop.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders
     * Request body: { productId, quantity }
     * Response body: { status, reason, inventory }
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/orders/{orderId}/cancel
     * Cancels order and restocks inventory
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/orders
     * Returns full order history for activity audit and dashboard feed
     */
    @GetMapping
    public ResponseEntity<List<OrderRecord>> getOrders() {
        return ResponseEntity.ok(orderService.getOrderHistory());
    }
}
