package com.ecommerce.app.module.banner;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class FestivalMessageRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FestivalMessageRepository.class);

    // ── Public: resolve a share slug → message + festival info ───────────
    public Optional<FestivalMessage> findBySlug(String shareSlug) throws SQLException {
        String sql = """
                SELECT m.message_id, m.festival_id, m.image_id, m.recipient_name,
                       m.message_text, m.sender_name, m.share_slug, m.is_active,
                       m.created_at, m.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_message m
                JOIN   festival_category f ON f.festival_id = m.festival_id
                WHERE  m.share_slug = ? AND m.is_active = TRUE
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, shareSlug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    // ── Admin: list all messages (newest first) ───────────────────────────
    public List<FestivalMessage> findAll() throws SQLException {
        String sql = """
                SELECT m.message_id, m.festival_id, m.image_id, m.recipient_name,
                       m.message_text, m.sender_name, m.share_slug, m.is_active,
                       m.created_at, m.created_by,
                       f.name AS festival_name, f.slug AS festival_slug
                FROM   festival_message m
                JOIN   festival_category f ON f.festival_id = m.festival_id
                ORDER BY m.created_at DESC
                """;
        List<FestivalMessage> list = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    // ── Create → auto-generates a unique 8-char slug ─────────────────────
    // adminId may be null when called by an unauthenticated user on /festival
    public FestivalMessage create(FestivalMessage msg, UUID adminId) throws SQLException {
        String slug = generateSlug();

        String sql = """
                INSERT INTO festival_message
                    (festival_id, image_id, recipient_name, message_text,
                     sender_name, share_slug, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING message_id, created_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, msg.getFestivalId());
            if (msg.getImageId() != null) ps.setObject(2, msg.getImageId());
            else                          ps.setNull(2, java.sql.Types.OTHER);
            ps.setString(3, msg.getRecipientName());
            ps.setString(4, msg.getMessageText());
            ps.setString(5, msg.getSenderName());
            ps.setString(6, slug);
            if (adminId != null) ps.setObject(7, adminId);
            else                 ps.setNull(7, java.sql.Types.OTHER);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    msg.setMessageId ((UUID) rs.getObject("message_id"));
                    msg.setShareSlug (slug);
                    msg.setCreatedAt (rs.getTimestamp("created_at"));
                    msg.setCreatedBy (adminId);
                    msg.setActive    (true);
                }
            }
        }
        return msg;
    }

    // ── Admin: toggle active/inactive ────────────────────────────────────
    public boolean setActive(UUID messageId, boolean active) throws SQLException {
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE festival_message SET is_active=? WHERE message_id=?")) {
            ps.setBoolean(1, active);
            ps.setObject (2, messageId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private FestivalMessage map(ResultSet rs) throws SQLException {
        FestivalMessage m = new FestivalMessage();
        m.setMessageId  ((UUID) rs.getObject("message_id"));
        m.setFestivalId ((UUID) rs.getObject("festival_id"));
        Object img = rs.getObject("image_id");
        if (img != null) m.setImageId((UUID) img);
        m.setFestivalName(rs.getString("festival_name"));
        m.setFestivalSlug(rs.getString("festival_slug"));
        m.setRecipientName(rs.getString("recipient_name"));
        m.setMessageText(rs.getString("message_text"));
        m.setSenderName (rs.getString("sender_name"));
        m.setShareSlug  (rs.getString("share_slug"));
        m.setActive     (rs.getBoolean("is_active"));
        m.setCreatedAt  (rs.getTimestamp("created_at"));
        Object cb = rs.getObject("created_by");
        if (cb != null) m.setCreatedBy((UUID) cb);
        return m;
    }

    /**
     * Cryptographically random 8-character URL-safe slug (Base64-URL).
     * 64^8 ≈ 281 trillion possibilities — collision-free at scale.
     */
    private static String generateSlug() {
        byte[] bytes = new byte[6];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes);
    }
}
