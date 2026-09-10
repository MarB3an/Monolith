package edu.cit.pescante;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.pescante.inventory.InventoryItem;
import edu.cit.pescante.inventory.InventoryRepository;
import edu.cit.pescante.shop.OrderRecord;
import edu.cit.pescante.shop.OrderRepository;
import edu.cit.pescante.shop.dto.CreateOrderRequest;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
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
    @DisplayName("POST /api/orders confirmed path: sufficient stock reserves item and records order")
    void testConfirmedOrderPath() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("P100", 2);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.reason", containsString("reserved")))
                .andExpect(jsonPath("$.inventory.productId", is("P100")))
                .andExpect(jsonPath("$.inventory.stock", is(23)));

        // Verify inventory table updated
        InventoryItem item = inventoryRepository.findById("P100").orElseThrow();
        assertEquals(23, item.getStock());

        // Verify orders table record written
        List<OrderRecord> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        OrderRecord order = orders.get(0);
        assertEquals("P100", order.getProductId());
        assertEquals(2, order.getQuantity());
        assertEquals("CONFIRMED", order.getStatus());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    @DisplayName("POST /api/orders rejected path: requested quantity exceeds stock (P300 stock=0)")
    void testRejectedOrderOutOfStock() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("P300", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.reason", containsString("exceeds available stock")))
                .andExpect(jsonPath("$.inventory.productId", is("P300")))
                .andExpect(jsonPath("$.inventory.stock", is(0)));

        // Verify inventory stock remained 0
        InventoryItem item = inventoryRepository.findById("P300").orElseThrow();
        assertEquals(0, item.getStock());

        // Verify orders table recorded the rejected attempt
        List<OrderRecord> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        OrderRecord order = orders.get(0);
        assertEquals("P300", order.getProductId());
        assertEquals(1, order.getQuantity());
        assertEquals("REJECTED", order.getStatus());
        assertTrue(order.getReason().contains("exceeds available stock"));
    }

    @Test
    @DisplayName("POST /api/orders rejected path: quantity exceeds available stock (P200 stock=10, requested=15)")
    void testRejectedOrderExcessQuantity() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("P200", 15);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.reason", containsString("exceeds available stock")))
                .andExpect(jsonPath("$.inventory.stock", is(10)));

        // Verify inventory stock remained 10
        InventoryItem item = inventoryRepository.findById("P200").orElseThrow();
        assertEquals(10, item.getStock());

        // Verify order record
        List<OrderRecord> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        assertEquals("REJECTED", orders.get(0).getStatus());
    }

    @Test
    @DisplayName("POST /api/orders rejected path: product does not exist")
    void testRejectedOrderNonExistentProduct() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("NON_EXISTENT", 1);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.reason", containsString("not found")));
    }
}
