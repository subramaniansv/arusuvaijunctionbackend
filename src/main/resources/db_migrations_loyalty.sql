-- ============================================================
-- LOYALTY CARD (QR CHECK-IN) MIGRATION
-- Idempotent: safe to run multiple times.
-- Run after the base schema + db_migrations.sql.
--
-- Feature:
--   A physical/online QR resolves to a loyalty check-in flow.
--   An authenticated user who scans it raises a PENDING check-in.
--   An admin approves/rejects it (the "is he there?" notification).
--   Each approved check-in = one visit tracked for that calendar day.
--   Online orders auto-create an APPROVED check-in for the order day,
--   so both offline (QR + admin approval) and online (order) visits
--   land in the same loyalty ledger.
-- ============================================================

-- 1. One loyalty card per user (created lazily on first check-in).
CREATE TABLE IF NOT EXISTS loyalty_cards (
    card_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    card_code    VARCHAR(24) NOT NULL UNIQUE,  -- short human-shareable code
    total_visits INT NOT NULL DEFAULT 0,       -- denormalized count of APPROVED check-ins
    points       INT NOT NULL DEFAULT 0,       -- 1 point per approved visit (extensible)
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_loyalty_cards_user_id ON loyalty_cards(user_id);

-- 2. Check-ins (one row per scan / order visit).
--    status: PENDING (awaiting admin), APPROVED, REJECTED
--    source: QR  (offline scan), ORDER (online auto)
--    visit_day: the calendar day the visit counts for (UTC date).
CREATE TABLE IF NOT EXISTS loyalty_checkins (
    checkin_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id     UUID NOT NULL REFERENCES loyalty_cards(card_id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source      VARCHAR(12) NOT NULL DEFAULT 'QR',
    status      VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    visit_day   DATE NOT NULL DEFAULT (CURRENT_DATE),
    order_id    UUID,                          -- set when source = ORDER
    note        VARCHAR(255),
    approved_by UUID,                          -- admin user_id who actioned it
    approved_at TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_card_id   ON loyalty_checkins(card_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_user_id   ON loyalty_checkins(user_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_status    ON loyalty_checkins(status);
CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_visit_day ON loyalty_checkins(visit_day);

-- 3. Enforce at most ONE visit counted per user per day.
--    A partial unique index ignores REJECTED rows so a user can retry
--    after a rejection, but can't double-count an approved/pending day.
CREATE UNIQUE INDEX IF NOT EXISTS uq_loyalty_one_visit_per_day
    ON loyalty_checkins (user_id, visit_day)
    WHERE status IN ('PENDING', 'APPROVED');

-- 4. Spend-aware check-ins.
--    purchase_amount: the ₹ value the customer spent in-store for this visit,
--    recorded by the admin at approval time so offline visits earn points the
--    SAME way online orders do (loyalty_settings.order_point_rate).
--    points_awarded: how many points this visit actually credited.
ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS purchase_amount NUMERIC(12,2);
ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS points_awarded  INT;
