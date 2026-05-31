package com.ecommerce.app.module.order;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * One-time migration: backfill variant_label on order_items where it's NULL
 * but a variant_id exists (or the product has only one variant).
 */
public class FixVariantMigration {
    private static final Logger LOG = LoggerFactory.getLogger(FixVariantMigration.class);

    public static void run() {
        if (alreadyMigrated()) {
            LOG.info("[MIGRATION] Variant constraints already in place — skipping");
            return;
        }
        fixCartUniqueConstraint();
        backfillVariantLabels();
    }

    private static boolean alreadyMigrated() {
        String check = """
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'uq_cart_product_variant'
            """;
        try (Connection conn = DBConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(check)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private static void fixCartUniqueConstraint() {
        LOG.info("[MIGRATION] Fixing cart unique constraint to allow multiple variants...");
        String dropOld = "ALTER TABLE cart_items DROP CONSTRAINT IF EXISTS uq_cart_product";
        String createNew = """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_product_variant
            ON cart_items (cart_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid))
            """;
        String dropOldOrder = "ALTER TABLE order_items DROP CONSTRAINT IF EXISTS uq_order_product";
        String createNewOrder = """
            CREATE UNIQUE INDEX IF NOT EXISTS uq_order_product_variant
            ON order_items (order_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid))
            """;
        try (Connection conn = DBConfig.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(dropOld);
            st.execute(createNew);
            st.execute(dropOldOrder);
            st.execute(createNewOrder);
            LOG.info("[MIGRATION] Cart + order_items constraints fixed: now allows multiple variants per product");
        } catch (Exception e) {
            LOG.error("[MIGRATION] Failed to fix constraints", e);
        }
    }

    private static void backfillVariantLabels() {
        LOG.info("[MIGRATION] Backfilling variant_label on order_items...");
        String sql = """
            UPDATE order_items oi
            SET variant_id = pv.variant_id,
                variant_label = pv.label
            FROM (
                SELECT DISTINCT ON (pv2.product_id) pv2.variant_id, pv2.label, pv2.product_id
                FROM product_variants pv2
                WHERE pv2.is_active = true
                ORDER BY pv2.product_id, pv2.sort_order, pv2.created_at
            ) pv
            WHERE oi.variant_label IS NULL
              AND oi.product_id = pv.product_id
            """;
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int updated = ps.executeUpdate();
            LOG.info("[MIGRATION] Updated {} order_items with variant labels", updated);
        } catch (Exception e) {
            LOG.error("[MIGRATION] Failed to backfill variant_label", e);
        }
    }
}
