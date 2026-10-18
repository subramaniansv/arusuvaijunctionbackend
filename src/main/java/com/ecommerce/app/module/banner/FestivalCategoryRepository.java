package com.ecommerce.app.module.banner;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class FestivalCategoryRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FestivalCategoryRepository.class);

    // ── Public: the ONE currently-active festival (spotlight) ─────────────
    // Business rule: only one festival is live on the client at a time.
    // Returns empty if admin has disabled all of them.
    public Optional<FestivalCategory> findActive() throws SQLException {
        String sql = """
                SELECT festival_id, name, slug, display_order, is_active, created_at
                FROM   festival_category
                WHERE  is_active = TRUE
                ORDER BY display_order
                LIMIT  1
                """;
        List<FestivalCategory> rows = query(sql);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    // ── Public: all active festivals (kept for compatibility) ─────────────
    public List<FestivalCategory> findAllActive() throws SQLException {
        String sql = """
                SELECT festival_id, name, slug, display_order, is_active, created_at
                FROM   festival_category
                WHERE  is_active = TRUE
                ORDER BY display_order
                """;
        return query(sql);
    }

    // ── Admin: all festivals including inactive ───────────────────────────
    public List<FestivalCategory> findAll() throws SQLException {
        String sql = """
                SELECT festival_id, name, slug, display_order, is_active, created_at
                FROM   festival_category
                ORDER BY display_order
                """;
        return query(sql);
    }

    // ── Admin: create ─────────────────────────────────────────────────────
    public FestivalCategory create(FestivalCategory fc) throws SQLException {
        String sql = """
                INSERT INTO festival_category (name, slug, display_order, is_active)
                VALUES (?, ?, ?, FALSE)
                RETURNING festival_id, created_at
                """;
        // New festivals start DISABLED — admin explicitly turns one on.
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fc.getName());
            ps.setString(2, fc.getSlug());
            ps.setInt   (3, fc.getDisplayOrder());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    fc.setFestivalId((UUID) rs.getObject("festival_id"));
                    fc.setCreatedAt(rs.getTimestamp("created_at"));
                    fc.setActive(false);
                }
            }
        }
        return fc;
    }

    // ── Admin: turn ON exactly one festival (disables all others) ─────────
    // This is the spotlight switch: enabling a festival makes it the ONLY
    // live one. Done in a single transaction so the client never sees two
    // active festivals at once.
    public boolean activateExclusive(UUID festivalId) throws SQLException {
        try (Connection c = DBConfig.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1. disable every festival
                try (PreparedStatement off = c.prepareStatement(
                        "UPDATE festival_category SET is_active = FALSE WHERE is_active = TRUE")) {
                    off.executeUpdate();
                }
                // 2. enable just this one
                int n;
                try (PreparedStatement on = c.prepareStatement(
                        "UPDATE festival_category SET is_active = TRUE WHERE festival_id = ?")) {
                    on.setObject(1, festivalId);
                    n = on.executeUpdate();
                }
                c.commit();
                return n > 0;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        }
    }

    // ── Admin: turn a single festival OFF (no spotlight after) ────────────
    public boolean deactivate(UUID festivalId) throws SQLException {
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE festival_category SET is_active = FALSE WHERE festival_id = ?")) {
            ps.setObject(1, festivalId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Admin: toggle helper — enable=spotlight, disable=single off ───────
    public boolean setActive(UUID festivalId, boolean active) throws SQLException {
        return active ? activateExclusive(festivalId) : deactivate(festivalId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private List<FestivalCategory> query(String sql) throws SQLException {
        List<FestivalCategory> list = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private FestivalCategory map(ResultSet rs) throws SQLException {
        FestivalCategory fc = new FestivalCategory();
        fc.setFestivalId((UUID) rs.getObject("festival_id"));
        fc.setName(rs.getString("name"));
        fc.setSlug(rs.getString("slug"));
        fc.setDisplayOrder(rs.getInt("display_order"));
        fc.setActive(rs.getBoolean("is_active"));
        fc.setCreatedAt(rs.getTimestamp("created_at"));
        return fc;
    }
}
