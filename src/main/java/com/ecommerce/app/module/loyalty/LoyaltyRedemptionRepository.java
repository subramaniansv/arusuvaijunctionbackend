package com.ecommerce.app.module.loyalty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.config.DBConfig;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access for loyalty settings + point redemptions.
 *
 * All point deductions use a guarded UPDATE
 *   {@code UPDATE loyalty_cards SET points = points - ? WHERE card_id = ? AND points >= ?}
 * so a redemption can never drive a balance negative, even under concurrency.
 * The affected-row count tells us whether the deduction succeeded.
 */
public class LoyaltyRedemptionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyRedemptionRepository.class);

    private final LoyaltyRepository cardRepo = new LoyaltyRepository();

    // ---- settings -----------------------------------------------------------

    /** The configured point->cash rate. Falls back to ₹10 if the row is missing. */
    public BigDecimal getPointValue() {
        String sql = "SELECT point_value_inr FROM loyalty_settings WHERE id = 1";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                BigDecimal v = rs.getBigDecimal("point_value_inr");
                if (v != null) return v;
            }
        } catch (SQLException e) {
            LOG.error("sql exception at getPointValue ", e);
        }
        return new BigDecimal("10.00");
    }

    public boolean setPointValue(BigDecimal value, UUID adminId) {
        String sql = """
                INSERT INTO loyalty_settings (id, point_value_inr, updated_by, updated_at)
                VALUES (1, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE
                    SET point_value_inr = EXCLUDED.point_value_inr,
                        updated_by      = EXCLUDED.updated_by,
                        updated_at      = CURRENT_TIMESTAMP
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, value);
            ps.setObject(2, adminId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at setPointValue ", e);
        }
        return false;
    }

    // ---- online redemption (inside caller's payment txn) --------------------

    /**
     * Deduct points and write an APPROVED ONLINE redemption, using the supplied
     * connection (the payment transaction). Returns the redemption, or null if
     * the guarded deduction failed (insufficient balance) — the caller must then
     * not apply the discount.
     */
    public LoyaltyRedemption redeemOnline(Connection c, UUID userId, int points,
                                          BigDecimal pointValue, UUID orderId) throws SQLException {
        LoyaltyCard card = cardRepo.getOrCreateCard(userId);
        if (card == null) return null;

        if (!deductPoints(c, card.getCardId(), points)) {
            return null; // insufficient balance
        }
        BigDecimal cash = pointValue.multiply(BigDecimal.valueOf(points));
        String sql = """
                INSERT INTO loyalty_redemptions
                    (card_id, user_id, channel, status, points_used, point_value, cash_value, order_id, decided_at)
                VALUES (?, ?, 'ONLINE', 'APPROVED', ?, ?, ?, ?, CURRENT_TIMESTAMP)
                RETURNING redemption_id, card_id, user_id, channel, status, points_used,
                          point_value, cash_value, order_id, note, decided_by, decided_at,
                          created_at, updated_at
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, card.getCardId());
            ps.setObject(2, userId);
            ps.setInt(3, points);
            ps.setBigDecimal(4, pointValue);
            ps.setBigDecimal(5, cash);
            ps.setObject(6, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    // ---- offline redemption (request -> admin decision) ---------------------

    /**
     * Reserve points and create a PENDING OFFLINE redemption in its own
     * transaction. Returns null if the balance is insufficient.
     */
    public LoyaltyRedemption requestOffline(UUID userId, int points, BigDecimal pointValue, String note) {
        LoyaltyCard card = cardRepo.getOrCreateCard(userId);
        if (card == null) return null;

        Connection c = null;
        try {
            c = DBConfig.getConnection();
            c.setAutoCommit(false);

            if (!deductPoints(c, card.getCardId(), points)) {
                c.rollback();
                return null; // insufficient balance
            }
            BigDecimal cash = pointValue.multiply(BigDecimal.valueOf(points));
            String sql = """
                    INSERT INTO loyalty_redemptions
                        (card_id, user_id, channel, status, points_used, point_value, cash_value, note)
                    VALUES (?, ?, 'OFFLINE', 'PENDING', ?, ?, ?, ?)
                    RETURNING redemption_id, card_id, user_id, channel, status, points_used,
                              point_value, cash_value, order_id, note, decided_by, decided_at,
                              created_at, updated_at
                    """;
            LoyaltyRedemption out = null;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, card.getCardId());
                ps.setObject(2, userId);
                ps.setInt(3, points);
                ps.setBigDecimal(4, pointValue);
                ps.setBigDecimal(5, cash);
                ps.setString(6, note);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) out = mapRow(rs);
                }
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            safeRollback(c);
            LOG.error("sql exception at requestOffline ", e);
        } finally {
            closeQuietly(c);
        }
        return null;
    }

    /**
     * Approve or reject a PENDING OFFLINE redemption.
     *   approve -> status APPROVED (points stay deducted; discount is confirmed).
     *   reject  -> status REJECTED AND the reserved points are refunded to the card.
     * Returns true only when a PENDING row was transitioned.
     */
    public boolean decideOffline(UUID redemptionId, boolean approve, UUID adminId, String note) {
        Connection c = null;
        try {
            c = DBConfig.getConnection();
            c.setAutoCommit(false);

            // Lock + read the pending row.
            UUID cardId;
            int points;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT card_id, points_used FROM loyalty_redemptions "
                    + "WHERE redemption_id = ? AND status = 'PENDING' AND channel = 'OFFLINE' FOR UPDATE")) {
                ps.setObject(1, redemptionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    cardId = (UUID) rs.getObject("card_id");
                    points = rs.getInt("points_used");
                }
            }

            String newStatus = approve ? "APPROVED" : "REJECTED";
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE loyalty_redemptions SET status = ?, decided_by = ?, "
                    + "decided_at = CURRENT_TIMESTAMP, note = COALESCE(?, note), "
                    + "updated_at = CURRENT_TIMESTAMP WHERE redemption_id = ?")) {
                ps.setString(1, newStatus);
                ps.setObject(2, adminId);
                ps.setString(3, note);
                ps.setObject(4, redemptionId);
                ps.executeUpdate();
            }

            // Reject -> refund the reserved points.
            if (!approve) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE loyalty_cards SET points = points + ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE card_id = ?")) {
                    ps.setInt(1, points);
                    ps.setObject(2, cardId);
                    ps.executeUpdate();
                }
            }

            c.commit();
            return true;
        } catch (SQLException e) {
            safeRollback(c);
            LOG.error("sql exception at decideOffline ", e);
        } finally {
            closeQuietly(c);
        }
        return false;
    }

    // ---- feeds --------------------------------------------------------------

    public List<LoyaltyRedemption> listUserRedemptions(UUID userId, int limit, int offset) {
        String sql = "SELECT * FROM loyalty_redemptions WHERE user_id = ? "
                + "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<LoyaltyRedemption> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at listUserRedemptions ", e);
        }
        return out;
    }

    public List<LoyaltyRedemption> listRedemptions(int limit, int offset, String statusFilter) {
        StringBuilder sb = new StringBuilder("""
                SELECT lr.*, u.email AS user_email,
                       (u.first_name || ' ' || COALESCE(u.last_name, '')) AS user_name
                FROM loyalty_redemptions lr
                JOIN users u ON u.user_id = lr.user_id
                """);
        boolean hasFilter = statusFilter != null && !statusFilter.isBlank();
        if (hasFilter) sb.append(" WHERE lr.status = ? ");
        sb.append(" ORDER BY lr.created_at DESC LIMIT ? OFFSET ?");

        List<LoyaltyRedemption> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            if (hasFilter) ps.setString(idx++, statusFilter);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoyaltyRedemption r = mapRow(rs);
                    r.setUserEmail(rs.getString("user_email"));
                    r.setUserName(rs.getString("user_name"));
                    out.add(r);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at listRedemptions ", e);
        }
        return out;
    }

    // ---- helpers ------------------------------------------------------------

    /** Guarded point deduction: succeeds only if the balance is sufficient. */
    private boolean deductPoints(Connection c, UUID cardId, int points) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE loyalty_cards SET points = points - ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE card_id = ? AND points >= ?")) {
            ps.setInt(1, points);
            ps.setObject(2, cardId);
            ps.setInt(3, points);
            return ps.executeUpdate() > 0;
        }
    }

    private LoyaltyRedemption mapRow(ResultSet rs) throws SQLException {
        LoyaltyRedemption r = new LoyaltyRedemption();
        r.setRedemptionId((UUID) rs.getObject("redemption_id"));
        r.setCardId((UUID) rs.getObject("card_id"));
        r.setUserId((UUID) rs.getObject("user_id"));
        r.setChannel(rs.getString("channel"));
        r.setStatus(rs.getString("status"));
        r.setPointsUsed(rs.getInt("points_used"));
        r.setPointValue(rs.getBigDecimal("point_value"));
        r.setCashValue(rs.getBigDecimal("cash_value"));
        r.setOrderId((UUID) rs.getObject("order_id"));
        r.setNote(rs.getString("note"));
        r.setDecidedBy((UUID) rs.getObject("decided_by"));
        r.setDecidedAt(rs.getTimestamp("decided_at"));
        r.setCreatedAt(rs.getTimestamp("created_at"));
        r.setUpdatedAt(rs.getTimestamp("updated_at"));
        return r;
    }

    private void safeRollback(Connection c) {
        if (c != null) try { c.rollback(); } catch (SQLException ignore) { }
    }

    private void closeQuietly(Connection c) {
        if (c != null) try { c.setAutoCommit(true); c.close(); } catch (SQLException ignore) { }
    }
}
