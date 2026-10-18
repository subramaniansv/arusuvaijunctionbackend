package com.ecommerce.app.module.banner;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time, idempotent migration for the "spotlight" festival model.
 *
 *  • Adds festival_message.image_id       (the exact bg the visitor chose)
 *  • Adds festival_message.recipient_name ("TO" line on the postcard)
 *  • Adds festival_message.sender_name    ("FROM" line on the postcard)
 *  • Enforces the rule that at most ONE festival_category is active at a
 *    time (partial unique index), turning everything off first so the
 *    constraint can be created cleanly.
 *
 * Runs on startup from Main, after the server is listening. Safe to run
 * repeatedly — every statement uses IF NOT EXISTS / is a no-op when the
 * column or index already exists.
 */
public final class FestivalSpotlightMigration {

    private static final Logger LOG =
            LoggerFactory.getLogger(FestivalSpotlightMigration.class);

    private FestivalSpotlightMigration() {}

    public static void run() {
        if (alreadyMigrated()) {
            LOG.info("[MIGRATION] Festival spotlight already in place — ensuring postcard columns");
            ensurePostcardColumns();
            return;
        }
        LOG.info("[MIGRATION] Applying festival spotlight schema changes…");
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {

            // 1. new columns on festival_message
            st.execute("""
                ALTER TABLE festival_message
                    ADD COLUMN IF NOT EXISTS image_id UUID
                        REFERENCES festival_image(image_id) ON DELETE SET NULL
                """);
            st.execute("""
                ALTER TABLE festival_message
                    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(120)
                """);
            st.execute("""
                ALTER TABLE festival_message
                    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(120)
                """);
            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_fm_image_id
                    ON festival_message (image_id)
                """);

            // 2. enforce single-active-festival spotlight.
            boolean multipleActive = countActiveFestivals() > 1;
            if (multipleActive) {
                LOG.info("[MIGRATION] >1 active festival found — keeping the first, disabling the rest");
                st.execute("""
                    UPDATE festival_category
                    SET is_active = FALSE
                    WHERE festival_id NOT IN (
                        SELECT festival_id FROM festival_category
                        WHERE is_active = TRUE
                        ORDER BY display_order
                        LIMIT 1
                    ) AND is_active = TRUE
                    """);
            }
            st.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_festival_single_active
                    ON festival_category ((TRUE)) WHERE is_active = TRUE
                """);

            LOG.info("[MIGRATION] Festival spotlight schema applied ✓");
        } catch (Exception e) {
            LOG.error("[MIGRATION] Festival spotlight migration failed", e);
        }
    }

    /** Idempotently add recipient_name in case an earlier version of the
     *  spotlight migration ran without it. */
    private static void ensurePostcardColumns() {
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {
            st.execute("""
                ALTER TABLE festival_message
                    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(120)
                """);
            st.execute("""
                ALTER TABLE festival_message
                    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(120)
                """);
        } catch (Exception e) {
            LOG.warn("[MIGRATION] ensurePostcardColumns failed", e);
        }
    }

    /** True when image_id + sender_name already exist on festival_message. */
    private static boolean alreadyMigrated() {
        String sql = """
            SELECT COUNT(*) AS n
            FROM information_schema.columns
            WHERE table_name = 'festival_message'
              AND column_name IN ('image_id', 'sender_name')
            """;
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("n") >= 2;
        } catch (Exception e) {
            LOG.warn("[MIGRATION] spotlight pre-check failed (will attempt migration)", e);
        }
        return false;
    }

    private static int countActiveFestivals() {
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) AS n FROM festival_category WHERE is_active = TRUE")) {
            if (rs.next()) return rs.getInt("n");
        } catch (Exception ignored) {}
        return 0;
    }
}
