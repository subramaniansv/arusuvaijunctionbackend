package com.ecommerce.app.module.banner;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class FestivalImageRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FestivalImageRepository.class);

    // ── Public: all active images for a festival, in display_order ────────
    public List<FestivalImage> findActiveByFestival(UUID festivalId) throws SQLException {
        String sql = """
                SELECT i.image_id, i.festival_id, i.image_url, i.object_key,
                       i.label, i.display_order, i.is_active, i.created_at, i.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_image i
                JOIN   festival_category f ON f.festival_id = i.festival_id
                WHERE  i.festival_id = ? AND i.is_active = TRUE
                ORDER BY i.display_order
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, festivalId);
            return collect(ps);
        }
    }

    // ── Public: one random active image from a festival (for share-page bg) ─
    public Optional<FestivalImage> findRandomActive(UUID festivalId) throws SQLException {
        String sql = """
                SELECT i.image_id, i.festival_id, i.image_url, i.object_key,
                       i.label, i.display_order, i.is_active, i.created_at, i.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_image i
                JOIN   festival_category f ON f.festival_id = i.festival_id
                WHERE  i.festival_id = ? AND i.is_active = TRUE
                ORDER BY RANDOM()
                LIMIT  1
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, festivalId);
            List<FestivalImage> rows = collect(ps);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
    }

    // ── Public: all active images across ALL active festivals (home carousel) ─
    public List<FestivalImage> findAllActive() throws SQLException {
        String sql = """
                SELECT i.image_id, i.festival_id, i.image_url, i.object_key,
                       i.label, i.display_order, i.is_active, i.created_at, i.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_image i
                JOIN   festival_category f ON f.festival_id = i.festival_id
                WHERE  i.is_active = TRUE AND f.is_active = TRUE
                ORDER BY f.display_order, i.display_order
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            return collect(ps);
        }
    }

    // ── Admin: all images for a festival (including inactive) ─────────────
    public List<FestivalImage> findAllByFestival(UUID festivalId) throws SQLException {
        String sql = """
                SELECT i.image_id, i.festival_id, i.image_url, i.object_key,
                       i.label, i.display_order, i.is_active, i.created_at, i.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_image i
                JOIN   festival_category f ON f.festival_id = i.festival_id
                WHERE  i.festival_id = ?
                ORDER BY i.display_order
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, festivalId);
            return collect(ps);
        }
    }

    // ── Admin: find by id (needed before delete to get objectKey) ─────────
    public Optional<FestivalImage> findById(UUID imageId) throws SQLException {
        String sql = """
                SELECT i.image_id, i.festival_id, i.image_url, i.object_key,
                       i.label, i.display_order, i.is_active, i.created_at, i.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_image i
                JOIN   festival_category f ON f.festival_id = i.festival_id
                WHERE  i.image_id = ?
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, imageId);
            List<FestivalImage> rows = collect(ps);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
    }

    // ── Admin: insert ─────────────────────────────────────────────────────
    public FestivalImage create(FestivalImage img, UUID adminId) throws SQLException {
        String sql = """
                INSERT INTO festival_image
                    (festival_id, image_url, object_key, label, display_order, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING image_id, created_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, img.getFestivalId());
            ps.setString(2, img.getImageUrl());
            ps.setString(3, img.getObjectKey());
            ps.setString(4, img.getLabel());
            ps.setInt   (5, img.getDisplayOrder());
            ps.setObject(6, adminId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    img.setImageId((UUID) rs.getObject("image_id"));
                    img.setCreatedAt(rs.getTimestamp("created_at"));
                    img.setActive(true);
                    img.setCreatedBy(adminId);
                }
            }
        }
        return img;
    }

    // ── Admin: soft-disable (keeps row + storage for audit) ───────────────
    public boolean disable(UUID imageId) throws SQLException {
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE festival_image SET is_active=FALSE WHERE image_id=?")) {
            ps.setObject(1, imageId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Admin: hard-delete (caller must already have purged storage) ───────
    public boolean delete(UUID imageId) throws SQLException {
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM festival_image WHERE image_id=?")) {
            ps.setObject(1, imageId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private List<FestivalImage> collect(PreparedStatement ps) throws SQLException {
        List<FestivalImage> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private FestivalImage map(ResultSet rs) throws SQLException {
        FestivalImage i = new FestivalImage();
        i.setImageId    ((UUID) rs.getObject("image_id"));
        i.setFestivalId ((UUID) rs.getObject("festival_id"));
        i.setFestivalName(rs.getString("festival_name"));
        i.setFestivalSlug(rs.getString("festival_slug"));
        i.setImageUrl   (rs.getString("image_url"));
        i.setObjectKey  (rs.getString("object_key"));
        i.setLabel      (rs.getString("label"));
        i.setDisplayOrder(rs.getInt("display_order"));
        i.setActive     (rs.getBoolean("is_active"));
        i.setCreatedAt  (rs.getTimestamp("created_at"));
        Object cb = rs.getObject("created_by");
        if (cb != null) i.setCreatedBy((UUID) cb);
        return i;
    }
}
