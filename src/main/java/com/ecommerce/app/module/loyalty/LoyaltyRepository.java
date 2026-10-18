package com.ecommerce.app.module.loyalty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.config.DBConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access for the loyalty card + check-in ledger.
 *
 * Design notes:
 *  - {@link #getOrCreateCard} is upsert-style so the caller never has to worry
 *    about whether the card already exists.
 *  - The partial unique index {@code uq_loyalty_one_visit_per_day} means
 *    inserting a second PENDING/APPROVED visit for the same user+day throws a
 *    unique-violation; the service layer translates that into a friendly
 *    "already checked in today" message.
 *  - {@link #approve} denormalizes total_visits/points onto the card inside the
 *    same transaction so the card summary stays consistent.
 */
public class LoyaltyRepository {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyRepository.class);

    // ---- card ---------------------------------------------------------------

    public LoyaltyCard findCardByUser(UUID userId) {
        String sql = "SELECT * FROM loyalty_cards WHERE user_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCard(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findCardByUser loyalty ", e);
        }
        return null;
    }

    /**
     * Return the user's card, creating it on first use. The card_code is a
     * short unique token derived from a random UUID. On a race (two scans at
     * once) the INSERT ... ON CONFLICT DO NOTHING loses gracefully and we
     * re-select the winner's row.
     */
    public LoyaltyCard getOrCreateCard(UUID userId) {
        LoyaltyCard existing = findCardByUser(userId);
        if (existing != null) return existing;

        String code = "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sql = "INSERT INTO loyalty_cards (user_id, card_code) VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO NOTHING";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("sql exception at getOrCreateCard loyalty ", e);
        }
        return findCardByUser(userId);
    }

    // ---- check-ins ----------------------------------------------------------

    /**
     * Insert a new check-in. Returns the created row, or null if the user has
     * already got a PENDING/APPROVED visit for that day (unique violation) or
     * on any other SQL error.
     */
    public LoyaltyCheckin insertCheckin(LoyaltyCheckin ci) {
        String sql = """
                INSERT INTO loyalty_checkins
                    (card_id, user_id, source, status, order_id, purchase_amount, points_awarded, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING checkin_id, card_id, user_id, source, status, visit_day,
                          order_id, purchase_amount, points_awarded, note,
                          approved_by, approved_at, created_at, updated_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, ci.getCardId());
            ps.setObject(2, ci.getUserId());
            ps.setString(3, ci.getSource() == null ? "QR" : ci.getSource());
            ps.setString(4, ci.getStatus() == null ? "PENDING" : ci.getStatus());
            ps.setObject(5, ci.getOrderId());
            ps.setBigDecimal(6, ci.getPurchaseAmount());
            if (ci.getPointsAwarded() != null) ps.setInt(7, ci.getPointsAwarded());
            else ps.setNull(7, java.sql.Types.INTEGER);
            ps.setString(8, ci.getNote());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCheckin(rs);
            }
        } catch (SQLException e) {
            // 23505 = unique_violation -> already checked in today.
            if ("23505".equals(e.getSQLState())) {
                LOG.info("duplicate loyalty check-in blocked for user {}", ci.getUserId());
            } else {
                LOG.error("sql exception at insertCheckin loyalty ", e);
            }
        }
        return null;
    }

    /** True when the user already has a PENDING/APPROVED visit for today. */
    public boolean hasVisitToday(UUID userId) {
        String sql = "SELECT 1 FROM loyalty_checkins "
                + "WHERE user_id = ? AND visit_day = CURRENT_DATE "
                + "AND status IN ('PENDING','APPROVED') LIMIT 1";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.error("sql exception at hasVisitToday loyalty ", e);
        }
        return false;
    }

    public LoyaltyCheckin findCheckin(UUID checkinId) {
        String sql = "SELECT * FROM loyalty_checkins WHERE checkin_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, checkinId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapCheckin(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findCheckin loyalty ", e);
        }
        return null;
    }

    /**
     * Approve a PENDING check-in and bump the card counters in one transaction.
     * Returns true only when a PENDING row was actually transitioned (so a
     * double-approve is a no-op).
     *
     * @param purchaseAmount the in-store spend recorded by the admin (nullable).
     * @param points         points to credit the card for this visit (>= 0).
     *                       The caller computes this from the configured
     *                       order_point_rate so offline + online points match.
     * The visit's purchase_amount and points_awarded are persisted on the row
     * for audit, and the card's points/total_visits are bumped by {@code points}.
     */
    public boolean approve(UUID checkinId, UUID adminId, java.math.BigDecimal purchaseAmount, int points) {
        int pts = Math.max(0, points);
        String updCheckin = "UPDATE loyalty_checkins "
                + "SET status = 'APPROVED', approved_by = ?, approved_at = CURRENT_TIMESTAMP, "
                + "    purchase_amount = ?, points_awarded = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE checkin_id = ? AND status = 'PENDING' "
                + "RETURNING card_id";
        String updCard = "UPDATE loyalty_cards "
                + "SET total_visits = total_visits + 1, points = points + ?, "
                + "    updated_at = CURRENT_TIMESTAMP "
                + "WHERE card_id = ?";
        Connection c = null;
        try {
            c = DBConfig.getConnection();
            c.setAutoCommit(false);
            UUID cardId;
            try (PreparedStatement ps = c.prepareStatement(updCheckin)) {
                ps.setObject(1, adminId);
                ps.setBigDecimal(2, purchaseAmount);
                ps.setInt(3, pts);
                ps.setObject(4, checkinId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { c.rollback(); return false; }
                    cardId = (UUID) rs.getObject("card_id");
                }
            }
            try (PreparedStatement ps = c.prepareStatement(updCard)) {
                ps.setInt(1, pts);
                ps.setObject(2, cardId);
                ps.executeUpdate();
            }
            c.commit();
            return true;
        } catch (SQLException e) {
            LOG.error("sql exception at approve loyalty ", e);
            safeRollback(c);
        } finally {
            closeQuietly(c);
        }
        return false;
    }

    /** Reject a PENDING check-in. Returns true if a row was transitioned. */
    public boolean reject(UUID checkinId, UUID adminId, String note) {
        String sql = "UPDATE loyalty_checkins "
                + "SET status = 'REJECTED', approved_by = ?, approved_at = CURRENT_TIMESTAMP, "
                + "    note = COALESCE(?, note), updated_at = CURRENT_TIMESTAMP "
                + "WHERE checkin_id = ? AND status = 'PENDING'";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, adminId);
            ps.setString(2, note);
            ps.setObject(3, checkinId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at reject loyalty ", e);
        }
        return false;
    }

    /** Admin feed: list check-ins, optionally filtered by status, newest first. */
    public List<LoyaltyCheckin> listCheckins(int limit, int offset, String statusFilter) {
        StringBuilder sb = new StringBuilder("""
                SELECT lc.*, u.email AS user_email,
                       (u.first_name || ' ' || COALESCE(u.last_name, '')) AS user_name
                FROM loyalty_checkins lc
                JOIN users u ON u.user_id = lc.user_id
                """);
        boolean hasFilter = statusFilter != null && !statusFilter.isBlank();
        if (hasFilter) sb.append(" WHERE lc.status = ? ");
        sb.append(" ORDER BY lc.created_at DESC LIMIT ? OFFSET ?");

        List<LoyaltyCheckin> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            if (hasFilter) ps.setString(idx++, statusFilter);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoyaltyCheckin ci = mapCheckin(rs);
                    ci.setUserEmail(rs.getString("user_email"));
                    ci.setUserName(rs.getString("user_name"));
                    out.add(ci);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at listCheckins loyalty ", e);
        }
        return out;
    }

    public int countCheckins(String statusFilter) {
        String sql = (statusFilter != null && !statusFilter.isBlank())
                ? "SELECT COUNT(*) FROM loyalty_checkins WHERE status = ?"
                : "SELECT COUNT(*) FROM loyalty_checkins";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (statusFilter != null && !statusFilter.isBlank()) ps.setString(1, statusFilter);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at countCheckins loyalty ", e);
        }
        return 0;
    }

    /** A user's own visit history, newest first. */
    public List<LoyaltyCheckin> listUserCheckins(UUID userId, int limit, int offset) {
        String sql = "SELECT * FROM loyalty_checkins WHERE user_id = ? "
                + "ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<LoyaltyCheckin> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapCheckin(rs));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at listUserCheckins loyalty ", e);
        }
        return out;
    }

    // ---- mappers / helpers --------------------------------------------------

    private LoyaltyCard mapCard(ResultSet rs) throws SQLException {
        LoyaltyCard card = new LoyaltyCard();
        card.setCardId((UUID) rs.getObject("card_id"));
        card.setUserId((UUID) rs.getObject("user_id"));
        card.setCardCode(rs.getString("card_code"));
        card.setTotalVisits(rs.getInt("total_visits"));
        card.setPoints(rs.getInt("points"));
        card.setCreatedAt(rs.getTimestamp("created_at"));
        card.setUpdatedAt(rs.getTimestamp("updated_at"));
        return card;
    }

    private LoyaltyCheckin mapCheckin(ResultSet rs) throws SQLException {
        LoyaltyCheckin ci = new LoyaltyCheckin();
        ci.setCheckinId((UUID) rs.getObject("checkin_id"));
        ci.setCardId((UUID) rs.getObject("card_id"));
        ci.setUserId((UUID) rs.getObject("user_id"));
        ci.setSource(rs.getString("source"));
        ci.setStatus(rs.getString("status"));
        ci.setVisitDay(rs.getDate("visit_day"));
        ci.setOrderId((UUID) rs.getObject("order_id"));
        try {
            ci.setPurchaseAmount(rs.getBigDecimal("purchase_amount"));
            int pa = rs.getInt("points_awarded");
            ci.setPointsAwarded(rs.wasNull() ? null : pa);
        } catch (SQLException ignore) {
            // Columns may be absent on a pre-migration schema; ignore gracefully.
        }
        ci.setNote(rs.getString("note"));
        ci.setApprovedBy((UUID) rs.getObject("approved_by"));
        ci.setApprovedAt(rs.getTimestamp("approved_at"));
        ci.setCreatedAt(rs.getTimestamp("created_at"));
        ci.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ci;
    }

    private void safeRollback(Connection c) {
        if (c != null) try { c.rollback(); } catch (SQLException ignore) { }
    }

    private void closeQuietly(Connection c) {
        if (c != null) try { c.setAutoCommit(true); c.close(); } catch (SQLException ignore) { }
    }
}
