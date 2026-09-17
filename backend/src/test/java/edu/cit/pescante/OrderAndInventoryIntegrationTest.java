package edu.cit.pescante;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.pescante.inventory.InventoryItem;
import edu.cit.pescante.inventory.InventoryRepository;
import edu.cit.pescante.notification.NotificationRecord;
import edu.cit.pescante.notification.NotificationRepository;
import edu.cit.pescante.shop.OrderRecord;
import edu.cit.pescante.shop.OrderRepository;
import edu.cit.pescante.shop.dto.CreateOrderRequest;
import edu.cit.pescante.shop.dto.OrderItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class OrderAndInventoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();

        // Seed with specifications:
        // P100 Wireless Mouse (25)
        // P200 Mechanical Keyboard (10)
        // P300 USB-C Hub (0)
        inventoryRepository.saveAll(List.of(
                new InventoryItem("P100", "Wireless Mouse", 25),
                new InventoryItem("P200", "Mechanical Keyboard", 10),
                new InventoryItem("P300", "USB-C Hub", 0)
        ));
    }

    @Test
    @DisplayName("GET /api/inventory returns seeded items")
    void testGetInventory() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.productId == 'P100')].stock").value(25))
                .andExpect(jsonPath("$[?(@.productId == 'P200')].stock").value(10))
                .andExpect(jsonPath("$[?(@.productId == 'P300')].stock").value(0));
    }

    @Test
    @DisplayName("POST /api/orders confirmed path: multi-item order where all items succeed")
    void testConfirmedMultiItemOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("P100", 2),
                new OrderItemRequest("P200", 3)
        ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.reason", containsString("reserved")))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].outcome", is("RESERVED")))
                .andExpect(jsonPath("$.items[1].outcome", is("RESERVED")));

        // Verify inventory table updated for both products
        assertEquals(23, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(7, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify orders and order_items records written
        List<OrderRecord> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        OrderRecord order = orders.get(0);
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals(2, order.getItems().size());

        // Verify Notification module received event
        List<NotificationRecord> notifications = notificationRepository.findAll();
        assertFalse(notifications.isEmpty());
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("confirmed")));
    }

    @Test
    @DisplayName("POST /api/orders rejected path: multi-item order with all-or-nothing rollback (no partial fulfillment)")
    void testRejectedMultiItemOrderRollback() throws Exception {
        // P100 has 25 (would succeed), P200 has 10 (fails with 15 requested)
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("P100", 2),
                new OrderItemRequest("P200", 15)
        ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.reason", containsString("exceeds available stock")))
                .andExpect(jsonPath("$.items[?(@.productId == 'P200')].outcome").value("EXCEEDS_STOCK"))
                .andExpect(jsonPath("$.items[?(@.productId == 'P100')].outcome").value("ROLLBACK_UNFULFILLED"));

        // ALL-OR-NOTHING VERIFICATION: P100 must NOT be reserved! Stock remains 25!
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock(),
                "P100 stock must not be deducted when order is rejected!");
        assertEquals(10, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify audit log recorded rejected status
        List<OrderRecord> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        assertEquals("REJECTED", orders.get(0).getStatus());

        // Verify Notification module logged rejected order
        List<NotificationRecord> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("rejected")));
    }

    @Test
    @DisplayName("Order Cancellation & Restock: POST /api/orders/{orderId}/cancel returns line items to inventory")
    void testOrderCancellationAndRestock() throws Exception {
        // 1. Place a confirmed multi-item order
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("P100", 5),
                new OrderItemRequest("P200", 4)
        ));

        String responseContent = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseContent).get("orderId").asLong();

        // Check stock reduced
        assertEquals(20, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(6, inventoryRepository.findById("P200").orElseThrow().getStock());

        // 2. Cancel the order
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.reason", containsString("restocked")));

        // 3. Verify stock is returned in inventory table
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(10, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify order record status updated to CANCELLED
        OrderRecord order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("CANCELLED", order.getStatus());

        // Verify Notification module recorded cancellation
        List<NotificationRecord> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("cancelled")));
    }

    @Test
    @DisplayName("Order Cancellation edge cases: 404 for missing order, 409 for already cancelled")
    void testOrderCancellationErrors() throws Exception {
        // 404 on non-existent order
        mockMvc.perform(post("/api/orders/99999/cancel"))
                .andExpect(status().isNotFound());

        // Create and cancel an order
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("P100", 1)));
        String responseContent = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseContent).get("orderId").asLong();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk());

        // 409 on second cancel attempt
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Low-Stock Auto-Reorder Rule: Remaining stock < 5 triggers LowStockEvent and notification")
    void testLowStockAlertTrigger() throws Exception {
        // P100 has 25. Order 22 units -> remaining stock becomes 3 (< 5 threshold)
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("P100", 22)));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        assertEquals(3, inventoryRepository.findById("P100").orElseThrow().getStock());

        // Verify notifications table has low stock alert
        List<NotificationRecord> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n ->
                n.getMessage().contains("Low stock alert") && n.getMessage().contains("P100") && n.getMessage().contains("reorder needed")),
                "Notification table must log reorder needed alert when stock drops below 5");
    }

    @Test
    @DisplayName("GET /api/notifications returns activity feed")
    void testGetNotifications() throws Exception {
        notificationRepository.save(new NotificationRecord("Order #1 confirmed"));
        notificationRepository.save(new NotificationRecord("Low stock alert: Product P200 - reorder needed"));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].message", notNullValue()));
    }
}
