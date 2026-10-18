-- ============================================================
-- HYPERLOCAL / QUICK DELIVERY (Rapido) MIGRATION
-- Idempotent: safe to run multiple times.
-- Run after the base schema + db_migrations.sql.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Product-level quick-delivery eligibility
--    Only selected products are quick-deliverable.
-- ------------------------------------------------------------
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS is_quick_delivery   BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS avg_picking_time_min INT     NOT NULL DEFAULT 5;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS max_quick_qty        INT     NOT NULL DEFAULT 10;

CREATE INDEX IF NOT EXISTS idx_products_quick ON products(is_quick_delivery)
    WHERE is_quick_delivery = TRUE;

-- ------------------------------------------------------------
-- 2. Stores / dark stores (pickup hubs for Rapido)
--    Each store has a dynamic service radius editable by admin.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quick_store (
    store_id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(150) NOT NULL,
    contact_name      VARCHAR(150),
    contact_phone     VARCHAR(20),
    address           TEXT        NOT NULL,
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    pincode           VARCHAR(6)  NOT NULL,
    -- dynamic radius: admin can change at any time; serviceability uses this live
    service_radius_km DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quick_store_active ON quick_store(is_active)
    WHERE is_active = TRUE;

-- ------------------------------------------------------------
-- 3. Admin-allowed delivery pincodes per store
--    "quick products can't be ordered other than pincode given by admin".
--    Serviceability = user pincode IN this table AND within radius.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quick_delivery_pincode (
    id          UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id    UUID       NOT NULL REFERENCES quick_store(store_id) ON DELETE CASCADE,
    pincode     VARCHAR(6) NOT NULL,
    is_active   BOOLEAN    NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (store_id, pincode)
);

CREATE INDEX IF NOT EXISTS idx_quick_pincode_lookup
    ON quick_delivery_pincode(pincode) WHERE is_active = TRUE;

-- ------------------------------------------------------------
-- 4. Per-store inventory for quick-delivery products
--    (Global products.stock_quantity stays for standard delivery.)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quick_store_inventory (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id        UUID    NOT NULL REFERENCES quick_store(store_id) ON DELETE CASCADE,
    product_id      UUID    NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    variant_id      UUID,  -- nullable; matches order_items.variant_id convention
    available_qty   INT     NOT NULL DEFAULT 0,
    reserved_qty    INT     NOT NULL DEFAULT 0,
    is_live         BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (store_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid))
);

CREATE INDEX IF NOT EXISTS idx_quick_inv_store_product
    ON quick_store_inventory(store_id, product_id);

-- ------------------------------------------------------------
-- 5. Order fulfillment-mode + hyperlocal fields
--    No mixing: an order is either STANDARD or HYPERLOCAL.
-- ------------------------------------------------------------
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS fulfillment_mode  VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS quick_store_id    UUID REFERENCES quick_store(store_id);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS drop_lat          DOUBLE PRECISION;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS drop_lng          DOUBLE PRECISION;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivery_eta_min  INT;

-- ------------------------------------------------------------
-- 6. Delivery job (provider-agnostic; Rapido is the first provider).
--    Kept separate from `orders` so the order model stays provider-neutral.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS delivery_job (
    delivery_job_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id          UUID        NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    provider          VARCHAR(20) NOT NULL DEFAULT 'RAPIDO',
    provider_quote_id VARCHAR(100),
    provider_order_id VARCHAR(100),
    status            VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    fee               NUMERIC(10,2),
    eta_min           INT,
    rider_name        VARCHAR(150),
    rider_phone       VARCHAR(20),
    tracking_url      TEXT,
    raw_payload       JSONB,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_delivery_job_provider_order
    ON delivery_job(provider_order_id);

-- ------------------------------------------------------------
-- 7. Delivery quote cache (short TTL; re-validated before booking).
--    Stores the fee + rider-availability snapshot shown to the user.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS delivery_quote (
    quote_id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL,
    store_id          UUID        NOT NULL REFERENCES quick_store(store_id),
    drop_lat          DOUBLE PRECISION NOT NULL,
    drop_lng          DOUBLE PRECISION NOT NULL,
    drop_pincode      VARCHAR(6)  NOT NULL,
    fee               NUMERIC(10,2) NOT NULL,
    eta_min           INT         NOT NULL,
    distance_km       DOUBLE PRECISION,
    rider_available   BOOLEAN     NOT NULL DEFAULT TRUE,
    provider_quote_id VARCHAR(100),
    expires_at        TIMESTAMP   NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_delivery_quote_user ON delivery_quote(user_id);
