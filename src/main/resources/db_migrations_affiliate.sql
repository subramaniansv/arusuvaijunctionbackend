-- ============================================================
-- AFFILIATE / REFERRAL MIGRATION
-- Idempotent: safe to run multiple times.
-- Mirror of AffiliateMigration.java (auto-runs on startup).
-- Run after db_migrations_loyalty.sql (needs loyalty_settings, loyalty_cards).
--
-- Feature:
--   An affiliate (any user) creates a shareable link (optionally product-scoped).
--   A visitor clicking ?ref=CODE gets a 15-day aj_ref cookie. When they register
--   or log in, the referral is bound to their account (survives cookie loss;
--   first-touch wins). If they place a qualifying (PAID) order within the window,
--   the AFFILIATE earns loyalty points — credited to the SAME loyalty_cards.points
--   column used by offline visits and online orders (backward compatible).
--
-- Anti-abuse:
--   * UNIQUE(referred_user_id)  -> one attribution per user ever (no re-earning
--     by re-registering the same person).
--   * No self-referral / same-email self-dealing (enforced in service layer).
--   * UNIQUE(order_id) on conversions -> one award per order (idempotent).
-- ============================================================

CREATE TABLE IF NOT EXISTS affiliate_links (
    link_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(16) NOT NULL UNIQUE,
    affiliate_id  UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    product_id    UUID REFERENCES products(product_id) ON DELETE CASCADE,
    target_path   VARCHAR(255) NOT NULL DEFAULT '/',
    clicks        INT NOT NULL DEFAULT 0,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_affiliate_links_affiliate ON affiliate_links(affiliate_id);
CREATE INDEX IF NOT EXISTS idx_affiliate_links_code      ON affiliate_links(code);

CREATE TABLE IF NOT EXISTS affiliate_attributions (
    attribution_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referred_user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    link_id          UUID NOT NULL REFERENCES affiliate_links(link_id) ON DELETE CASCADE,
    affiliate_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    attributed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP NOT NULL,
    consumed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_affiliate_attr_affiliate ON affiliate_attributions(affiliate_id);
CREATE INDEX IF NOT EXISTS idx_affiliate_attr_referred  ON affiliate_attributions(referred_user_id);

CREATE TABLE IF NOT EXISTS affiliate_conversions (
    conversion_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attribution_id   UUID NOT NULL REFERENCES affiliate_attributions(attribution_id) ON DELETE CASCADE,
    order_id         UUID NOT NULL UNIQUE,
    affiliate_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    referred_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    order_total      NUMERIC(12,2) NOT NULL,
    points_awarded   INT NOT NULL,
    reversed         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_affiliate_conv_affiliate ON affiliate_conversions(affiliate_id);

-- Admin-configurable rates live on the shared loyalty_settings singleton.
ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS order_point_rate     NUMERIC(10,4) NOT NULL DEFAULT 0;
ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_point_rate  NUMERIC(10,4) NOT NULL DEFAULT 0;
ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_flat_points INT           NOT NULL DEFAULT 0;
ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_window_days INT           NOT NULL DEFAULT 15;
