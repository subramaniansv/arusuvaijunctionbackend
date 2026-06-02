
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

-- 6. Forgot-password: self-service password reset tokens.
-- Only SHA-256 hashes of the raw token are stored (never the raw token), so a
-- DB leak can't reveal active reset links. Mirrors email_verification_tokens.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id
    ON password_reset_tokens(user_id);
