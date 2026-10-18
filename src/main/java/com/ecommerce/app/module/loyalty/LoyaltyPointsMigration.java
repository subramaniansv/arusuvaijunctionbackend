package com.ecommerce.app.module.loyalty;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time, idempotent migration for loyalty point redemption:
 *   • loyalty_settings     (singleton: configurable point->cash rate; default ₹10)
 *   • loyalty_redemptions  (ledger of point spends: ONLINE auto, OFFLINE approved)
 *
 * Runs after {@link LoyaltyMigration} (which creates loyalty_cards). All
 * statements use IF NOT EXISTS so it is safe to run repeatedly.
 */
public final class LoyaltyPointsMigration {

    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyPointsMigration.class);

    private LoyaltyPointsMigration() {}

    public static void run() {
        if (alreadyMigrated()) {
            LOG.info("[MIGRATION] Loyalty points tables already present — skipping");
            return;
        }
        LOG.info("[MIGRATION] Applying loyalty points schema…");
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS loyalty_settings (
                    id              INT PRIMARY KEY DEFAULT 1,
                    point_value_inr NUMERIC(10,2) NOT NULL DEFAULT 10.00,
                    updated_by      UUID,
                    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT loyalty_settings_singleton CHECK (id = 1)
                )
                """);
            // Seed the singleton row with the default rate (1 point = ₹10).
            st.execute("""
                INSERT INTO loyalty_settings (id, point_value_inr)
                VALUES (1, 10.00)
                ON CONFLICT (id) DO NOTHING
                """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS loyalty_redemptions (
                    redemption_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    card_id       UUID NOT NULL REFERENCES loyalty_cards(card_id) ON DELETE CASCADE,
                    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                    channel       VARCHAR(10) NOT NULL DEFAULT 'OFFLINE',  -- ONLINE | OFFLINE
                    status        VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | CANCELLED
                    points_used   INT NOT NULL,
                    point_value   NUMERIC(10,2) NOT NULL,
                    cash_value    NUMERIC(12,2) NOT NULL,
                    order_id      UUID,
                    note          VARCHAR(255),
                    decided_by    UUID,
                    decided_at    TIMESTAMP,
                    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT loyalty_redemptions_points_positive CHECK (points_used > 0)
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_redemptions_user_id ON loyalty_redemptions(user_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_redemptions_status  ON loyalty_redemptions(status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_loyalty_redemptions_order   ON loyalty_redemptions(order_id)");

            LOG.info("[MIGRATION] Loyalty points schema applied ✓");
        } catch (Exception e) {
            LOG.error("[MIGRATION] Loyalty points migration failed", e);
        }
    }

    private static boolean alreadyMigrated() {
        String sql = """
            SELECT COUNT(*) AS n
            FROM information_schema.tables
            WHERE table_name IN ('loyalty_settings', 'loyalty_redemptions')
            """;
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("n") >= 2;
        } catch (Exception e) {
            LOG.warn("[MIGRATION] loyalty points pre-check failed (will attempt migration)", e);
        }
        return false;
    }
}
