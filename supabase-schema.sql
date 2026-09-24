-- ==============================================================================
-- Supabase (PostgreSQL) Schema and Seed Script for Monolith Shop Activity
-- Lab 3: LegacySupply Anti-Corruption Layer added supplier_orders table
-- ==============================================================================

-- Drop existing tables to recreate cleanly from scratch
DROP TABLE IF EXISTS supplier_orders CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory CASCADE;

-- 1. Create inventory table
CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    stock INTEGER NOT NULL CHECK (stock >= 0)
);

-- 2. Create orders table (supports CONFIRMED, REJECTED, CANCELLED)
CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL, -- "CONFIRMED", "REJECTED", "CANCELLED"
    reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. Create order_items table for multi-item orders
CREATE TABLE IF NOT EXISTS order_items (
    item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

-- 4. Create notifications table for in-monolith domain event log
CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    message VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 5. Create supplier_orders table (Lab 3 - LegacySupply ACL)
--    Tracks every reorder placed with LegacySupply.
--    Status column uses our domain enum, never LegacySupply status codes directly.
CREATE TABLE IF NOT EXISTS supplier_orders (
    id             BIGSERIAL PRIMARY KEY,
    product_id     VARCHAR(50)  NOT NULL,
    buyer_ref      VARCHAR(50)  UNIQUE,
    request_id     VARCHAR(100) UNIQUE,
    po_number      VARCHAR(50),
    cases          INTEGER      NOT NULL,
    units          INTEGER      NOT NULL,
    status         VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 6. Seed initial inventory products
INSERT INTO inventory (product_id, name, stock) VALUES
    ('P100', 'Wireless Mouse', 25),
    ('P200', 'Mechanical Keyboard', 10),
    ('P300', 'USB-C Hub', 0)
ON CONFLICT (product_id) DO UPDATE 
SET name = EXCLUDED.name, stock = EXCLUDED.stock;

-- Verify seeded data
SELECT * FROM inventory;
SELECT * FROM supplier_orders;
