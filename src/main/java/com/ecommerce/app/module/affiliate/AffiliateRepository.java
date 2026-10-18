package com.ecommerce.app.module.affiliate;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access for the affiliate / referral feature.
 *
 * Integrity guarantees baked in here:
 *  - A link code is globally unique (table constraint) and generated with a
 *    retry loop on collision.
 *  - One active attribution per referred user: {@code affiliate_attributions
 *    .referred_user_id} is UNIQUE, so a second bind for the same user fails
 *    with 23505 and we keep the first-touch row.
 *  - One award per order: {@code affiliate_conversions.order_id} is UNIQUE;
 *    {@link #conversionExistsForOrder} short-circuits a double award and the
 *    unique index is the final backstop under concurrency.
 */
public class AffiliateRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AffiliateRepository.class);

    // ---- links --------------------------------------------------------------

    /** Create a link with a freshly generated unique code. Returns null on error. */
    public AffiliateLink createLink(UUID affiliateId, UUID productId, String targetPath) {
        String path = (targetPath == null || targetPath.isBlank()) ? "/" : targetPath.trim();
        String sql = """
                INSERT INTO affiliate_links (code, affiliate_id, product_id, target_path)
                VALUES (?, ?, ?, ?)
                RETURNING link_id, code, affiliate_id, product_id, target_path, clicks,
                          active, created_at, updated_at
                """;
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = generateCode();
            try (Connection c = DBConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, code);
                ps.setObject(2, affiliateId);
                ps.setObject(3, productId);
                ps.setString(4, path);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return mapLink(rs);
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    // code collision — retry with a new code
                    continue;
                }
                LOG.error("sql exception at createLink affiliate ", e);
                return null;
            }
        }
        LOG.error("createLink: exhausted code generation attempts");
        return null;
    }

    public AffiliateLink findActiveByCode(String code) {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT * FROM affiliate_links WHERE code = ? AND active = TRUE";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapLink(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findActiveByCode affiliate ", e);
        }
        return null;
    }

    public void incrementClicks(UUID linkId) {
        String sql = "UPDATE affiliate_links SET clicks = clicks + 1, updated_at = CURRENT_TIMESTAMP WHERE link_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, linkId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warn("incrementClicks failed: {}", e.getMessage());
        }
    }

    /** Links owned by an affiliate, enriched with conversion count + points earned. */
    public List<AffiliateLink> listByAffiliate(UUID affiliateId) {
        String sql = """
                SELECT l.*,
                       COALESCE(cv.n, 0)   AS conversions,
                       COALESCE(cv.pts, 0) AS points_earned
                FROM affiliate_links l
                LEFT JOIN (
                    SELECT a.link_id,
                           COUNT(*) FILTER (WHERE c.reversed = FALSE)             AS n,
                           SUM(CASE WHEN c.reversed = FALSE THEN c.points_awarded ELSE 0 END) AS pts
                    FROM affiliate_conversions c
                    JOIN affiliate_attributions a ON a.attribution_id = c.attribution_id
                    GROUP BY a.link_id
                ) cv ON cv.link_id = l.link_id
                WHERE l.affiliate_id = ?
                ORDER BY l.created_at DESC
                """;
        List<AffiliateLink> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, affiliateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AffiliateLink l = mapLink(rs);
                    l.setConversions(rs.getLong("conversions"));
                    l.setPointsEarned(rs.getLong("points_earned"));
                    out.add(l);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at listByAffiliate affiliate ", e);
        }
        return out;
    }

    // ---- attributions -------------------------------------------------------

    public AffiliateAttribution findAttributionByUser(UUID referredUserId) {
        String sql = "SELECT * FROM affiliate_attributions WHERE referred_user_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, referredUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAttribution(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findAttributionByUser affiliate ", e);
        }
        return null;
    }

    /**
     * Create the binding. Fails silently (returns null) if a row already exists
     * for this user (first-touch wins — 23505 on the UNIQUE referred_user_id).
     */
    public AffiliateAttribution createAttribution(UUID referredUserId, UUID linkId,
                                                  UUID affiliateId, Timestamp expiresAt) {
        String sql = """
                INSERT INTO affiliate_attributions
                    (referred_user_id, link_id, affiliate_id, expires_at)
                VALUES (?, ?, ?, ?)
                RETURNING attribution_id, referred_user_id, link_id, affiliate_id,
                          attributed_at, expires_at, consumed
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, referredUserId);
            ps.setObject(2, linkId);
            ps.setObject(3, affiliateId);
            ps.setTimestamp(4, expiresAt);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAttribution(rs);
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                LOG.info("attribution already exists for user {} (first-touch kept)", referredUserId);
            } else {
                LOG.error("sql exception at createAttribution affiliate ", e);
            }
        }
        return null;
    }

    // ---- conversions / awards ----------------------------------------------

    public boolean conversionExistsForOrder(Connection c, UUID orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM affiliate_conversions WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /**
     * Find the active, unconsumed, unexpired attribution for a buyer, FOR UPDATE
     * so two concurrent awards can't both pass the check. Returns null if none.
     */
    public AffiliateAttribution lockActiveAttribution(Connection c, UUID referredUserId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM affiliate_attributions "
                + "WHERE referred_user_id = ? AND consumed = FALSE AND expires_at > CURRENT_TIMESTAMP "
                + "FOR UPDATE")) {
            ps.setObject(1, referredUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAttribution(rs);
            }
        }
        return null;
    }

    /** Credit the affiliate's loyalty_cards.points balance (same column as visits/orders). */
    public void addPointsToAffiliate(Connection c, UUID affiliateId, int points) throws SQLException {
        // Ensure the affiliate has a card (upsert), then credit it.
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO loyalty_cards (user_id, card_code) VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO NOTHING")) {
            ps.setObject(1, affiliateId);
            ps.setString(2, "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loyalty_cards SET points = points + ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ?")) {
            ps.setInt(1, points);
            ps.setObject(2, affiliateId);
            ps.executeUpdate();
        }
    }

    public void insertConversion(Connection c, UUID attributionId, UUID orderId, UUID affiliateId,
                                 UUID referredUserId, double orderTotal, int points) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO affiliate_conversions "
                + "(attribution_id, order_id, affiliate_id, referred_user_id, order_total, points_awarded) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, attributionId);
            ps.setObject(2, orderId);
            ps.setObject(3, affiliateId);
            ps.setObject(4, referredUserId);
            ps.setBigDecimal(5, java.math.BigDecimal.valueOf(orderTotal));
            ps.setInt(6, points);
            ps.executeUpdate();
        }
    }

    public void markAttributionConsumed(Connection c, UUID attributionId, boolean consumed) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE affiliate_attributions SET consumed = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE attribution_id = ?")) {
            ps.setBoolean(1, consumed);
            ps.setObject(2, attributionId);
            ps.executeUpdate();
        }
    }

    // ---- refund reversal ----------------------------------------------------

    public AffiliateConversionRow findConversionByOrder(Connection c, UUID orderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT conversion_id, attribution_id, affiliate_id, points_awarded, reversed "
                + "FROM affiliate_conversions WHERE order_id = ? FOR UPDATE")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                AffiliateConversionRow r = new AffiliateConversionRow();
                r.conversionId  = (UUID) rs.getObject("conversion_id");
                r.attributionId = (UUID) rs.getObject("attribution_id");
                r.affiliateId   = (UUID) rs.getObject("affiliate_id");
                r.points        = rs.getInt("points_awarded");
                r.reversed      = rs.getBoolean("reversed");
                return r;
            }
        }
    }

    public void markConversionReversed(Connection c, UUID conversionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE affiliate_conversions SET reversed = TRUE WHERE conversion_id = ?")) {
            ps.setObject(1, conversionId);
            ps.executeUpdate();
        }
    }

    /** Subtract points from the affiliate but never drive the balance negative. */
    public void subtractPointsFromAffiliate(Connection c, UUID affiliateId, int points) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loyalty_cards SET points = GREATEST(0, points - ?), updated_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ?")) {
            ps.setInt(1, points);
            ps.setObject(2, affiliateId);
            ps.executeUpdate();
        }
    }

    // ---- summary ------------------------------------------------------------

    public long[] summaryForAffiliate(UUID affiliateId) {
        // returns { totalClicks, totalConversions, totalPoints }
        long clicks = 0, conv = 0, pts = 0;
        try (Connection c = DBConfig.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(clicks),0) FROM affiliate_links WHERE affiliate_id = ?")) {
                ps.setObject(1, affiliateId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) clicks = rs.getLong(1); }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(points_awarded),0) FROM affiliate_conversions "
                    + "WHERE affiliate_id = ? AND reversed = FALSE")) {
                ps.setObject(1, affiliateId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { conv = rs.getLong(1); pts = rs.getLong(2); }
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at summaryForAffiliate ", e);
        }
        return new long[]{clicks, conv, pts};
    }

    // ---- helpers ------------------------------------------------------------

    public static final class AffiliateConversionRow {
        public UUID conversionId;
        public UUID attributionId;
        public UUID affiliateId;
        public int points;
        public boolean reversed;
    }

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no ambiguous chars
    private final java.security.SecureRandom rnd = new java.security.SecureRandom();

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private AffiliateLink mapLink(ResultSet rs) throws SQLException {
        AffiliateLink l = new AffiliateLink();
        l.setLinkId((UUID) rs.getObject("link_id"));
        l.setCode(rs.getString("code"));
        l.setAffiliateId((UUID) rs.getObject("affiliate_id"));
        l.setProductId((UUID) rs.getObject("product_id"));
        l.setTargetPath(rs.getString("target_path"));
        l.setClicks(rs.getInt("clicks"));
        l.setActive(rs.getBoolean("active"));
        l.setCreatedAt(rs.getTimestamp("created_at"));
        l.setUpdatedAt(rs.getTimestamp("updated_at"));
        return l;
    }

    private AffiliateAttribution mapAttribution(ResultSet rs) throws SQLException {
        AffiliateAttribution a = new AffiliateAttribution();
        a.setAttributionId((UUID) rs.getObject("attribution_id"));
        a.setReferredUserId((UUID) rs.getObject("referred_user_id"));
        a.setLinkId((UUID) rs.getObject("link_id"));
        a.setAffiliateId((UUID) rs.getObject("affiliate_id"));
        a.setAttributedAt(rs.getTimestamp("attributed_at"));
        a.setExpiresAt(rs.getTimestamp("expires_at"));
        a.setConsumed(rs.getBoolean("consumed"));
        return a;
    }
}
