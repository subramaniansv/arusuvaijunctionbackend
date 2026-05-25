-- Run these ALTER TABLE statements once against your database.

-- 1. Store shipping fee per order
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_fee DOUBLE PRECISION NOT NULL DEFAULT 0;

-- 2. Track user last login time
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login TIMESTAMP WITH TIME ZONE;

-- 3. Saved addresses per user (one user can have many addresses)
CREATE TABLE IF NOT EXISTS user_addresses (
    address_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    label        VARCHAR(80),          -- e.g. "Home", "Office"
    full_name    VARCHAR(120) NOT NULL,
    phone        VARCHAR(30)  NOT NULL,
    line1        VARCHAR(160) NOT NULL,
    line2        VARCHAR(160),
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(80)  NOT NULL,
    pincode      VARCHAR(20)  NOT NULL,
    country      VARCHAR(60)  NOT NULL DEFAULT 'IN',
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_addresses_user_id ON user_addresses(user_id);
