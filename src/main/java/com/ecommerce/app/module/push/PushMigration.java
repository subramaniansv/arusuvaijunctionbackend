package com.ecommerce.app.module.push;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time, idempotent migration for Web Push subscriptions.
 *
 * Each row is one browser/device of one logged-in user. The push endpoint is
 * unique (that is the mailbox the push service gives us); p256dh + auth are the
 * subscription's public key material used to encrypt the payload (RFC 8291).
 *
 * A single user can have many rows (phone + laptop + desktop...). When a push
 * service returns 404/410 for an endpoint the subscription is dead and we
 * delete that row (handled in PushService).
 *
 * Safe to run repeatedly (IF NOT EXISTS everywhere).
 */
public final class PushMigration {

    private static final Logger LOG = LoggerFactory.getLogger(PushMigration.class);

    private PushMigration() {}

    public static void run() {
        if (alreadyMigrated()) {
            LOG.info("[MIGRATION] push_subscriptions already present — skipping");
            return;
        }
        LOG.info("[MIGRATION] Applying web-push schema…");
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS push_subscriptions (
                    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                    endpoint    TEXT NOT NULL UNIQUE,
                    p256dh      TEXT NOT NULL,
                    auth        TEXT NOT NULL,
                    user_agent  VARCHAR(400),
                    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_seen   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_push_subs_user_id ON push_subscriptions(user_id)");

            LOG.info("[MIGRATION] web-push schema applied ✓");
        } catch (Exception e) {
            LOG.error("[MIGRATION] push migration failed", e);
        }
    }

    private static boolean alreadyMigrated() {
        String sql = """
            SELECT COUNT(*) AS n
            FROM information_schema.tables
            WHERE table_name = 'push_subscriptions'
            """;
        try (Connection c = DBConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt("n") >= 1;
        } catch (Exception e) {
            LOG.warn("[MIGRATION] push pre-check failed (will attempt migration)", e);
        }
        return false;
    }
}
