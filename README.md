

### 1. Launch Spring Boot Backend
```bash
cd backend
./mvnw spring-boot:run
```
* Backend runs on: `http://localhost:8080`
* Connects directly to Supabase via Hibernate/JPA.

### 2. Launch React Frontend
```bash
cd frontend
npm install
npm run dev
```
* Frontend runs on: `http://localhost:5173`

---

## 🌐 Network Tab Evidence (Confirmed + Rejected Order)
<img width="1919" height="1075" alt="image" src="https://github.com/user-attachments/assets/77f1f982-98e1-4c85-8efb-3356d2a8e56f" />

### 1. Confirmed Order Path (Sufficient Stock)

* **HTTP Method & URL**: `POST http://localhost:8080/api/orders`
* **Status Code**: `200 OK`
* **Request Payload**:
  ```json
  {
    "productId": "P100",
    "quantity": 2
  }
  ```
* **Response Payload**:
  ```json
  {
    "orderId": 1,
    "status": "CONFIRMED",
    "reason": "Order placed successfully (2 item(s) reserved)",
    "inventory": {
      "productId": "P100",
      "name": "Wireless Mouse",
      "stock": 23
    },
    "createdAt": "2026-09-10T20:15:14.867679"
  }
  ```
* **Supabase Database Impact**:
  - `inventory` table: `P100` stock decremented from **25** to **23**.
  - `orders` table: New record created (`order_id: 1`, `product_id: P100`, `quantity: 2`, `status: CONFIRMED`).

---

### 2. Rejected Order Path (Insufficient Stock)

* **HTTP Method & URL**: `POST http://localhost:8080/api/orders`
* **Status Code**: `200 OK`
* **Request Payload**:
  ```json
  {
    "productId": "P200",
    "quantity": 15
  }
  ```
* **Response Payload**:
  ```json
  {
    "orderId": 2,
    "status": "REJECTED",
    "reason": "Requested quantity (15) exceeds available stock (10)",
    "inventory": {
      "productId": "P200",
      "name": "Mechanical Keyboard",
      "stock": 10
    },
    "createdAt": "2026-09-10T20:18:22.104231"
  }
  <img width="1919" height="1079" alt="image" src="https://github.com/user-attachments/assets/25567c74-9a60-4a14-9290-c561012eb915" />

  ```
* **Supabase Database Impact**:
  - `inventory` table: `P200` stock remains unchanged at **10**.
  - `orders` table: New audit record created (`order_id: 2`, `product_id: P200`, `quantity: 15`, `status: REJECTED`, `reason: Requested quantity (15) exceeds available stock (10)`).

---

## 📝 Architectural Reflection

### 1. In-Process vs. Separate Microservices Over a Network
Integrating the Order and Inventory modules in-process within a modular monolith gives us immense advantages **for free**:
- **Zero-Latency In-Memory Execution**: Method invocations occur directly in JVM memory via reference passing, bypassing serialization (JSON/Protobuf), socket allocation, and network transport latency.
- **ACID Transactional Guarantees**: A single `@Transactional` boundary spans both order creation and inventory deduction. If an unexpected runtime exception occurs, the database transaction rolls back atomically without data anomalies.
- **Zero Network Failure Modes**: There are no split-brain scenarios, partial network drops, or connection timeouts.

If we split these into separate microservices over a network, we would need to add significant infrastructure:
- **Resilience Patterns**: HTTP/gRPC clients (OpenFeign/WebClient), retry mechanisms with exponential backoff, and Circuit Breakers (Resilience4j).
- **Distributed Consistency**: Without shared database transactions, we must implement the **Saga Pattern** (orchestrated or choreographed) with compensating transactions to unreserve stock if order creation fails, or adopt an outbox pattern with an event broker (Kafka/RabbitMQ).
- **Operational Overhead**: Distributed tracing (OpenTelemetry/Zipkin), service discovery, API gateways, independent CI/CD pipelines, and health checks.

### 2. Why Package-Private Visibility on `InventoryServiceImpl` Matters
Package-private visibility (no modifier) on `InventoryServiceImpl` strictly enforces the architectural module boundary at compile time:
- **Compile-Time Encapsulation**: Only classes inside `edu.cit.pescante.inventory` can see or instantiate `InventoryServiceImpl`. External packages, such as `edu.cit.pescante.shop`, are prevented by the Java compiler from importing or type-casting to the concrete implementation.
- **Dependency Inversion**: `OrderService` is forced to depend exclusively on the public abstraction: the `InventoryService` interface injected through its constructor.
- **What Breaks if Made Public?**: If `InventoryServiceImpl` were declared `public`, developers or IDE auto-importers could bypass the interface, directly instantiate the implementation (`new InventoryServiceImpl()`), or invoke internal repository helper methods. This introduces tight coupling, bypasses Spring's transactional proxies, and creates architectural erosion where internal refactorings in Inventory inevitably break Shop.

### 3. When to Extract Inventory and Required Code Changes
We would extract Inventory into its own microservice under specific organizational and operational conditions:
- **Divergent Scalability**: When read-heavy inventory lookups (e.g., millions of shoppers browsing stock) vastly outnumber write transactions, requiring independent horizontal autoscaling.
- **Autonomous Team Ownership**: When separate engineering teams manage Warehouse/Fulfillment and E-Commerce storefronts with independent release cycles.
- **High Blast-Radius Isolation**: When inventory operations must stay online even if the shopping cart service crashes.

**Code Changes Needed to Extract**:
1. **Replace In-Memory Call**: In `OrderService`, replace the direct call to `inventoryService.reserve(productId, quantity)` with an HTTP/gRPC remote client (e.g., Spring `WebClient` or `@FeignClient`) calling `http://inventory-service/api/inventory/reserve`.
2. **DTO & Error Mapping**: Introduce remote exception handlers to handle network failures, timeouts, and HTTP 5xx responses.
3. **Decouple Data Stores**: Move the `inventory` table to a dedicated inventory database instance.
4. **Asynchronous / Saga Rollback**: Implement compensating transactions or event publishing (`InventoryReservedEvent`, `InventoryReservationFailedEvent`) over Kafka or RabbitMQ.
