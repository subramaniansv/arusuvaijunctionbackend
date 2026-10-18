
-- 4. Delhivery shipping integration: tracking columns on orders
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(50);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_provider VARCHAR(30) DEFAULT 'STATIC';

CREATE INDEX IF NOT EXISTS idx_orders_tracking_number ON orders(tracking_number);

-- 5. Fix cart unique constraint to allow multiple variants of the same product
ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS uq_cart_product;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_product_variant
    ON cart_items (cart_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- 6. Forgot-password: self-service password reset tokens.
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

-- ============================================================
-- 7. Festival Banner System  (replaces old hero_banner design)
--    DROP + recreate so we start clean.
-- ============================================================

-- 7a. Drop old tables (safe — they were freshly created, no prod data)
DROP TABLE IF EXISTS hero_banner_image  CASCADE;
DROP TABLE IF EXISTS hero_banner        CASCADE;
DROP INDEX  IF EXISTS uq_hero_banner_active;
DROP INDEX  IF EXISTS idx_hero_banner_active;

-- 7b. Festival categories
--     Admin manages this list (Pongal, Diwali, Onam …).
--     slug is used in the shareable URL: /festival?f=pongal
CREATE TABLE IF NOT EXISTS festival_category (
    festival_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL UNIQUE,   -- "Pongal"
    slug          VARCHAR(100) NOT NULL UNIQUE,   -- "pongal"  (URL-safe)
    display_order INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO festival_category (name, slug, display_order)
VALUES
    ('Pongal',    'pongal',    1),
    ('Diwali',    'diwali',    2),
    ('Onam',      'onam',      3),
    ('Christmas', 'christmas', 4),
    ('Eid',       'eid',       5),
    ('General',   'general',   99)
ON CONFLICT (slug) DO NOTHING;

-- 7c. Festival background images
--     Each image belongs to ONE festival.
--     Admin can upload many, disable them as seasons change.
CREATE TABLE IF NOT EXISTS festival_image (
    image_id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    festival_id   UUID         NOT NULL REFERENCES festival_category(festival_id) ON DELETE CASCADE,
    image_url     VARCHAR(1000) NOT NULL,    -- CDN public URL
    object_key    VARCHAR(500)  NOT NULL,    -- storage key for deletion
    label         VARCHAR(200),              -- optional admin note, e.g. "2025 Pongal hero"
    display_order INT          NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    UUID         REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_fi_festival_id ON festival_image (festival_id);
CREATE INDEX IF NOT EXISTS idx_fi_active      ON festival_image (is_active);

-- 7d. Festival messages
--     Admin writes a message → system generates a short unique slug.
--     Anyone who visits /festival?msg=<slug> sees:
--       · the message text
--       · a random active image from the chosen festival
CREATE TABLE IF NOT EXISTS festival_message (
    message_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    festival_id   UUID         NOT NULL REFERENCES festival_category(festival_id) ON DELETE CASCADE,
    message_text  TEXT         NOT NULL,             -- the actual greeting / announcement
    share_slug    VARCHAR(12)  NOT NULL UNIQUE,      -- short random ID, e.g. "k3pX9mQz"
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    UUID         REFERENCES users(user_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_fm_share_slug  ON festival_message (share_slug);
CREATE INDEX IF NOT EXISTS idx_fm_festival_id ON festival_message (festival_id);
CREATE INDEX IF NOT EXISTS idx_fm_active      ON festival_message (is_active);

-- ============================================================
-- 7e. Festival spotlight model (food business — one live festival)
-- ============================================================

-- Let the visitor pin the exact background image they chose, plus an
-- optional "from" name on the card.
ALTER TABLE festival_message
    ADD COLUMN IF NOT EXISTS image_id    UUID REFERENCES festival_image(image_id) ON DELETE SET NULL;
ALTER TABLE festival_message
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_fm_image_id ON festival_message (image_id);

-- Spotlight rule: at most ONE festival may be active at a time.
-- Start clean — turn everything off; admin explicitly enables one later.
UPDATE festival_category SET is_active = FALSE WHERE is_active = TRUE;

-- Partial unique index enforces "only one active festival" at the DB level.
CREATE UNIQUE INDEX IF NOT EXISTS uq_festival_single_active
    ON festival_category ((TRUE)) WHERE is_active = TRUE;
