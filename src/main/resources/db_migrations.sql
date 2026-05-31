
-- 4. Delhivery shipping integration: tracking columns on orders
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(50);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_provider VARCHAR(30) DEFAULT 'STATIC';

CREATE INDEX IF NOT EXISTS idx_orders_tracking_number ON orders(tracking_number);

-- 5. Fix cart unique constraint to allow multiple variants of the same product
-- Old constraint: UNIQUE(cart_id, product_id) — prevents adding 100g and 250g of same product
-- New constraint: UNIQUE(cart_id, product_id, variant_id) — allows different variants
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS uq_cart_product;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_product_variant
    ON cart_items (cart_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid));
