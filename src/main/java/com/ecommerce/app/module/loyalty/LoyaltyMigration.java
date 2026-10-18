package com.ecommerce.app.module.loyalty;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time, idempotent migration for the loyalty card (QR check-in) feature.
 * Mirrors the mirror of {@code db_migrations_loyalty.sql} so the schema is
 * created automatically on startup (the repo auto-runs migrations from Main;
 * the .sql file is kept for manual/DBA runs).
 *
 * Creates:
 *   • loyalty_cards      (one card per user)
 *   • loyalty_checkins   (ledger: QR scans + auto order visits)
 *   • uq_loyalty_one_visit_per_day (partial unique index: PENDING/APPROVED)
 *
 * Every statement uses IF NOT EXISTS so it is safe to run repeatedly.
 */
public final class LoyaltyMigration {

    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyMigration.class);

    private LoyaltyMigration() {}

    public static void run() {
        if (alreadyMigrated()) {
            LOG.info("[MIGRATION] Loyalty tables already present — ensuring spend columns");
            ensureSpendColumns();
            return;
        }
        LOG.info("[MIGRATION] Applying loyalty card schema…");
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS loyalty_cards (
                    card_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id      UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
                    card_code    VARCHAR(24) NOT NULL UNIQUE,
                    total_visits INT NOT NULL DEFAULT 0,
                    points       INT NOT NULL DEFAULT 0,
                    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_cards_user_id ON loyalty_cards(user_id)");

            st.execute("""
                CREATE TABLE IF NOT EXISTS loyalty_checkins (
                    checkin_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    card_id     UUID NOT NULL REFERENCES loyalty_cards(card_id) ON DELETE CASCADE,
                    user_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                    source      VARCHAR(12) NOT NULL DEFAULT 'QR',
                    status      VARCHAR(12) NOT NULL DEFAULT 'PENDING',
                    visit_day   DATE NOT NULL DEFAULT (CURRENT_DATE),
                    order_id    UUID,
                    note        VARCHAR(255),
                    approved_by UUID,
                    approved_at TIMESTAMP,
                    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_card_id   ON loyalty_checkins(card_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_user_id   ON loyalty_checkins(user_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_status    ON loyalty_checkins(status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_checkins_visit_day ON loyalty_checkins(visit_day)");

            st.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_loyalty_one_visit_per_day
                    ON loyalty_checkins (user_id, visit_day)
                    WHERE status IN ('PENDING', 'APPROVED')
                """);

            // Spend-aware check-ins: the admin records how much the customer
            // purchased in-store when approving a QR visit, so offline visits
            // earn points the SAME way online orders do (order_point_rate).
            // points_awarded records exactly how many points this visit credited.
            st.execute("ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS purchase_amount NUMERIC(12,2)");
            st.execute("ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS points_awarded  INT");

            LOG.info("[MIGRATION] Loyalty card schema applied ✓");
        } catch (Exception e) {
            LOG.error("[MIGRATION] Loyalty migration failed", e);
        }
    }

    /**
     * Idempotently add the spend-aware columns to an already-migrated
     * loyalty_checkins table (so existing installs pick them up on restart).
     */
    private static void ensureSpendColumns() {
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {
            st.execute("ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS purchase_amount NUMERIC(12,2)");
            st.execute("ALTER TABLE loyalty_checkins ADD COLUMN IF NOT EXISTS points_awarded  INT");
        } catch (Exception e) {
            LOG.warn("[MIGRATION] ensureSpendColumns failed (non-fatal): {}", e.getMessage());
        }
    }

    private static boolean alreadyMigrated() {
        String sql = """
            SELECT COUNT(*) AS n
            FROM information_schema.tables
            WHERE table_name IN ('loyalty_cards', 'loyalty_checkins')
            """;
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("n") >= 2;
        } catch (Exception e) {
            LOG.warn("[MIGRATION] loyalty pre-check failed (will attempt migration)", e);
        }
        return false;
    }
}
