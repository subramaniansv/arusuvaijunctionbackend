package com.ecommerce.app.module.push;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PushRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PushRepository.class);

    /**
     * Upsert by endpoint. If the same browser re-subscribes (same endpoint) we
     * update its keys + owner + last_seen instead of inserting a duplicate.
     */
    public void save(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        String sql = """
            INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, user_agent)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (endpoint) DO UPDATE
               SET user_id   = EXCLUDED.user_id,
                   p256dh    = EXCLUDED.p256dh,
                   auth      = EXCLUDED.auth,
                   user_agent= EXCLUDED.user_agent,
                   last_seen = CURRENT_TIMESTAMP
            """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, endpoint);
            ps.setString(3, p256dh);
            ps.setString(4, auth);
            ps.setString(5, userAgent);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error("push save failed", e);
            throw new RuntimeException("could not save subscription");
        }
    }

    /**
     * All subscriptions belonging to users who hold the {@code Admin} role.
     * Powers staff notifications (new paid order, check-in approval request,
     * points-usage approval request). One row per admin device.
     */
    public List<PushSubscription> findAdminSubscriptions() {
        String sql = """
            SELECT ps.id, ps.user_id, ps.endpoint, ps.p256dh, ps.auth, ps.user_agent
            FROM push_subscriptions ps
            JOIN user_role ur ON ur.user_id = ps.user_id
            JOIN role r       ON r.role_id  = ur.role_id
            WHERE LOWER(r.name) = 'admin'
            """;
        List<PushSubscription> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) {
            LOG.error("push findAdminSubscriptions failed", e);
        }
        return out;
    }

    public List<PushSubscription> findByUser(UUID userId) {
        String sql = "SELECT id,user_id,endpoint,p256dh,auth,user_agent FROM push_subscriptions WHERE user_id = ?";
        List<PushSubscription> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (Exception e) {
            LOG.error("push findByUser failed", e);
        }
        return out;
    }

    /** Delete one dead/unsubscribed endpoint. */
    public void deleteByEndpoint(String endpoint) {
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM push_subscriptions WHERE endpoint = ?")) {
            ps.setString(1, endpoint);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warn("push deleteByEndpoint failed: {}", e.getMessage());
        }
    }

    private PushSubscription map(ResultSet rs) throws Exception {
        PushSubscription s = new PushSubscription();
        s.setId(rs.getObject("id", UUID.class));
        s.setUserId(rs.getObject("user_id", UUID.class));
        s.setEndpoint(rs.getString("endpoint"));
        s.setP256dh(rs.getString("p256dh"));
        s.setAuth(rs.getString("auth"));
        s.setUserAgent(rs.getString("user_agent"));
        return s;
    }
}
