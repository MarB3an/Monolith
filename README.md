# Modular Monolith Order & Inventory System (Lab 2)

A modular monolith built with Spring Boot and React, demonstrating module boundaries, multi-item transactional orders with all-or-nothing rollback, order cancellation with inventory restock, and in-monolith domain events with asynchronous/synchronous notification logging.



##  Supabase Setup Steps

### Step 1: Create Supabase Project
1. Log in to [supabase.com](https://supabase.com) and create a new project.
2. Note your project reference ID (e.g., `txabiyeodojnqsxmlzwe`) and region (e.g., `ap-southeast-1`).

### Step 2: Run SQL Schema & Seed Script
Open the **SQL Editor** in your Supabase dashboard, paste the contents of [`supabase-schema.sql`](supabase-schema.sql), and run:

```sql
-- Drop existing tables to recreate cleanly from scratch
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.order_items CASCADE;
DROP TABLE IF EXISTS public.orders CASCADE;
DROP TABLE IF EXISTS public.inventory CASCADE;

-- 1. Create Inventory Table
CREATE TABLE IF NOT EXISTS public.inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

-- 2. Create Orders Table
CREATE TABLE IF NOT EXISTS public.orders (
    order_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL, -- "CONFIRMED", "REJECTED", "CANCELLED"
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. Create Order Items Table (Multi-Item line items)
CREATE TABLE IF NOT EXISTS public.order_items (
    item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES public.orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES public.inventory(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

-- 4. Create Notifications Table (Domain event activity feed)
CREATE TABLE IF NOT EXISTS public.notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    message VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Seed Initial Inventory Products
INSERT INTO public.inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
ON CONFLICT (product_id) DO UPDATE 
SET name = EXCLUDED.name, stock = EXCLUDED.stock;

SELECT * FROM public.inventory;
```

### Step 3: Configure Environment Variables
Create a `.env` file in the root directory containing your Supabase pooler credentials:
```env
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres.txabiyeodojnqsxmlzwe
SPRING_DATASOURCE_PASSWORD=YourSupabasePassword!
```

---

## Running the Application

### 1. Launch Spring Boot Backend
```bash
cd backend
./mvnw.cmd spring-boot:run
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

## Network Tab Evidence (All Four Scenarios)

### 1. Multi-Item Order Where All Items Succeed (CONFIRMED)

* **HTTP Method & URL**: `POST http://localhost:8080/api/orders`
* **Status Code**: `200 OK`
* **Request Payload**:
  ```json
  {
    "items": [
      { "productId": "P100", "quantity": 2 },
      { "productId": "P200", "quantity": 1 }
    ]
  }
  ```
* **Response Payload**:
  ```json
  {
    "status": "CONFIRMED",
    "reason": "Order placed successfully (2 item(s) reserved)",
    "items": [
      { "productId": "P100", "quantity": 2, "outcome": "RESERVED" },
      { "productId": "P200", "quantity": 1, "outcome": "RESERVED" }
    ],
    "inventory": [
      { "productId": "P300", "name": "USB-C Hub", "stock": 0 },
      { "productId": "P100", "name": "Wireless Mouse", "stock": 19 },
      { "productId": "P200", "name": "Mechanical Keyboard", "stock": 7 }
    ],
    "orderId": 9,
    "createdAt": "2026-09-17T18:59:12.1847909"
  }
<img width="1918" height="1030" alt="image" src="https://github.com/user-attachments/assets/271f8e5d-d2de-475d-86a8-62702b211418" />

  
* **Database & Event Impact**:
  - `inventory` table: `P100` stock decremented by 2, `P200` stock decremented by 1.
  - `orders` & `order_items` tables: Order record `#9` created with status `CONFIRMED` and 2 line items saved.
  - Domain event: `OrderPlacedEvent` published via `ApplicationEventPublisher`, consumed by `NotificationEventListener`, writing `"Order #9 confirmed"` to the `notifications` table.

---

### 2. Multi-Item Order Where One Item Fails (REJECTED with Rollback)

* **HTTP Method & URL**: `POST http://localhost:8080/api/orders`
* **Status Code**: `200 OK`
* **Context**: `P100` has 19 in stock (would succeed), but `P200` has only 7 in stock (fails with 15 requested).
* **Request Payload**:
  ```json
  {
    "items": [
      { "productId": "P100", "quantity": 2 },
      { "productId": "P200", "quantity": 15 }
    ]
  }
  ```
* **Response Payload**:
  ```json
  {
    "status": "REJECTED",
    "reason": "Requested quantity (15) exceeds available stock (7) for product P200",
    "items": [
      { "productId": "P100", "quantity": 2, "outcome": "ROLLBACK_UNFULFILLED" },
      { "productId": "P200", "quantity": 15, "outcome": "EXCEEDS_STOCK" }
    ],
    "inventory": [
      { "productId": "P300", "name": "USB-C Hub", "stock": 0 },
      { "productId": "P100", "name": "Wireless Mouse", "stock": 19 },
      { "productId": "P200", "name": "Mechanical Keyboard", "stock": 7 }
    ],
    "orderId": 10,
    "createdAt": "2026-09-17T18:59:20.0645848"
  }
  ```
  <img width="1902" height="1031" alt="image" src="https://github.com/user-attachments/assets/b9b7d404-716f-4687-8a8e-304e15bfcfe1" />

* **All-or-Nothing Rollback Verification**:
  - `P100` stock was **NOT** decremented—remained at **19**.
  - `P200` stock was **NOT** decremented—remained at **7**.
  - Pre-validation ensured `inventoryService.reserve()` was never called; zero partial fulfillment occurred.
  - Domain event: `OrderRejectedEvent` published, logging `"Order #10 rejected: Requested quantity (15) exceeds available stock (7) for product P200"` in `notifications`.

---

### 3. Order Cancellation with Restock Reflected in GET /api/inventory

* **HTTP Method & URL**: `POST http://localhost:8080/api/orders/9/cancel`
* **Status Code**: `200 OK`
* **Response Payload**:
  ```json
  {
    "status": "CANCELLED",
    "reason": "Order cancelled by user - all line items restocked to inventory",
    "items": [
      { "productId": "P100", "quantity": 2, "outcome": "RESTOCKED" },
      { "productId": "P200", "quantity": 1, "outcome": "RESTOCKED" }
    ],
    "inventory": [
      { "productId": "P300", "name": "USB-C Hub", "stock": 0 },
      { "productId": "P100", "name": "Wireless Mouse", "stock": 21 },
      { "productId": "P200", "name": "Mechanical Keyboard", "stock": 8 }
    ],
    "orderId": 9,
    "createdAt": "2026-09-17T18:59:12.184791"
  }
  ```
* **Subsequent Verification**: `GET http://localhost:8080/api/inventory`
* **Status Code**: `200 OK`
* **Response Payload**:
  ```json
  [
    { "productId": "P300", "name": "USB-C Hub", "stock": 0 },
    { "productId": "P100", "name": "Wireless Mouse", "stock": 21 },
    { "productId": "P200", "name": "Mechanical Keyboard", "stock": 8 }
  ]
  ```
* **Database & Restock Impact**:
  - `P100` stock returned from **19** back to **21**.
  - `P200` stock returned from **7** back to **8**.
  - Order status transitioned to `CANCELLED`.
  - Notification logged: `"Order #9 cancelled - line items restocked to inventory"`.

<img width="1917" height="1033" alt="image" src="https://github.com/user-attachments/assets/9eba1bed-9c12-412f-975e-108e9da54111" />


### 4. Notification Feed Showing Confirmed Order, Rejected Order, and Low-Stock Alert

* **Context**: Order `#11` was placed for 18 units of `P100` (reducing stock from 21 down to 3, dropping below threshold 5 and triggering `LowStockEvent`).
* **HTTP Method & URL**: `GET http://localhost:8080/api/notifications`
* **Status Code**: `200 OK`
* **Response Payload**:
  ```json
  [
    {
      "notificationId": 5,
      "message": "Order #11 confirmed",
      "createdAt": "2026-09-17T18:59:34.464186"
    },
    {
      "notificationId": 4,
      "message": "Low stock alert: Product P100 (Wireless Mouse) stock is 3 (threshold: 5) - reorder needed",
      "createdAt": "2026-09-17T18:59:34.298451"
    },
    {
      "notificationId": 3,
      "message": "Order #9 cancelled - line items restocked to inventory",
      "createdAt": "2026-09-17T18:59:27.389975"
    },
    {
      "notificationId": 2,
      "message": "Order #10 rejected: Requested quantity (15) exceeds available stock (7) for product P200",
      "createdAt": "2026-09-17T18:59:20.229612"
    },
    {
      "notificationId": 1,
      "message": "Order #9 confirmed",
      "createdAt": "2026-09-17T18:59:12.400525"
    }
  ]
  ```
* **Feed Summary**: Demonstrates real-time activity feed logging confirmed orders, rejected orders, cancellations, and the distinct **low-stock auto-reorder alert** triggered when stock fell below 5.

<img width="1919" height="798" alt="image" src="https://github.com/user-attachments/assets/ba35a074-8c82-4577-ac68-bf07e15d4599" />


## Architectural Reflection

In-Process Multi-Item Atomicity vs. Distributed Network Sagas

In our modular monolith, multi-item order atomicity is guaranteed through two mechanisms: Pre-Reservation Stock Checks and Local ACID Transactions. Before mutating state, OrderService checks all requested items against current stock via inventoryService.getItem(). If any item exceeds available stock, the transaction aborts before altering any inventory, eliminating partial reservations. Furthermore, the @Transactional boundary on placeOrder() ensures all inventory decrements and order record insertions share a single database transaction, triggering a database rollback via Spring and Hibernate if an unexpected runtime exception occurs. Conversely, if Order and Inventory were split across a network, shared database transactions would be impossible, requiring an Orchestrated or Choreographed Saga pattern. The Order service would issue reservation requests across HTTP/gRPC, and if an item failed midway, a saga coordinator would have to execute compensating transactions (such as calling a restock endpoint) to release previously reserved inventory, demanding idempotency keys, persistent saga logs, and eventual consistency handling to recover from transient network drops.

Event-Driven Decoupling and Microservice Integration for Notifications

Publishing domain events (OrderPlacedEvent, OrderRejectedEvent, OrderCancelledEvent) via Spring’s ApplicationEventPublisher eliminates direct coupling between OrderService and Notification. Instead of synchronous method invocations, OrderService simply publishes facts about domain state with zero knowledge of who subscribes, how many listeners exist, or whether notifications succeed. If Notification became a separate microservice, an external broker like RabbitMQ or Apache Kafka would replace Spring's in-process event bus to transport events asynchronously across the network. To achieve reliable at-least-once delivery, the Order service would implement the Transactional Outbox Pattern—persisting events to an outbox table within the order transaction and publishing via a CDC tool like Debezium—to prevent message loss during crashes. Furthermore, the Notification microservice would require idempotent consumers tracking processed event IDs to avoid duplicate alerts during network redeliveries, backed by Dead Letter Queues (DLQ) for malformed payloads.

First Microservice Extraction: The Notification Module

If forced to extract exactly one module first, Notification is the optimal choice due to its non-blocking flow, fault isolation, and pre-existing event boundary. Notification is purely a downstream consumer, meaning neither Order nor Inventory awaits a response from it, eliminating latency risks in the critical checkout path. If the Notification service goes down, core customer operations—browsing inventory, placing orders, and cancelling orders—remain completely operational. Additionally, Notification already communicates solely through immutable domain events without direct service calls. Executing this extraction requires replacing in-memory eventPublisher.publishEvent() calls in Shop and Inventory with a message producer (such as Spring KafkaTemplate) to publish domain event JSON to message topics. In Notification, an independent Spring Boot service must be created with its own database, replacing @EventListener in NotificationEventListener with @KafkaListener(topics = "order-events"). Finally, the notifications table must be migrated to a separate, isolated PostgreSQL database instance to complete physical and logical service decoupling.
**
