-- Run these ALTER TABLE statements once against your database.

-- 1. Store shipping fee per order
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_fee DOUBLE PRECISION NOT NULL DEFAULT 0;

-- 2. Track user last login time
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_login TIMESTAMP WITH TIME ZONE;
