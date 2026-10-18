package com.ecommerce.app.module.affiliate;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time, idempotent migration for the affiliate / referral feature.
 *
 * Tables:
 *   affiliate_links        — a shareable link owned by an affiliate (user),
 *                            optionally scoped to a product.
 *   affiliate_attributions — binds a referred user to the link that referred
 *                            them. Persisted at register/login so it survives
 *                            cookie loss. One active row per referred user
 *                            (first-touch wins).
 *   affiliate_conversions  — audit of each points award from a qualifying
 *                            order. UNIQUE(order_id) => idempotent.
 *
 * Settings (added to the EXISTING loyalty_settings singleton so admin rate
 * config stays in one place — backward compatible, columns added with
 * defaults):
 *   order_point_rate       — points earned per ₹1 of order total (buyer-side
 *                            "online order" points; default 0 preserves the
 *                            legacy flat "+1 per order" behaviour, see note).
 *   referral_point_rate    — points the AFFILIATE earns per ₹1 of a referred
 *                            order total (default 0 => disabled until admin sets it).
 *   referral_flat_points   — flat points per referred order (overrides rate when > 0).
 *   referral_window_days   — attribution window (default 15).
 *
 * Every statement uses IF NOT EXISTS / ADD COLUMN IF NOT EXISTS so it is safe
 * to run repeatedly.
 */
public final class AffiliateMigration {

    private static final Logger LOG = LoggerFactory.getLogger(AffiliateMigration.class);

    private AffiliateMigration() {}

    public static void run() {
        LOG.info("[MIGRATION] Applying affiliate / referral schema…");
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {

            // --- affiliate_links -------------------------------------------------
            st.execute("""
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
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_affiliate_links_affiliate ON affiliate_links(affiliate_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_affiliate_links_code      ON affiliate_links(code)");

            // --- affiliate_attributions -----------------------------------------
            // One active attribution per referred user (first-touch wins).
            st.execute("""
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
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_affiliate_attr_affiliate ON affiliate_attributions(affiliate_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_affiliate_attr_referred  ON affiliate_attributions(referred_user_id)");

            // --- affiliate_conversions ------------------------------------------
            // UNIQUE(order_id) guarantees one award per order (idempotency).
            st.execute("""
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
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_affiliate_conv_affiliate ON affiliate_conversions(affiliate_id)");

            // --- extend loyalty_settings with the configurable rates ------------
            st.execute("ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS order_point_rate     NUMERIC(10,4) NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_point_rate  NUMERIC(10,4) NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_flat_points INT           NOT NULL DEFAULT 0");
            st.execute("ALTER TABLE loyalty_settings ADD COLUMN IF NOT EXISTS referral_window_days INT           NOT NULL DEFAULT 15");

            LOG.info("[MIGRATION] Affiliate / referral schema applied ✓");
        } catch (Exception e) {
            LOG.error("[MIGRATION] Affiliate migration failed", e);
        }
    }

    public static boolean columnExists(String table, String column) {
        String sql = "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = ?";
        try (Connection c = DBConfig.getConnection();
             var ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (Exception e) {
            return false;
        }
    }
}
