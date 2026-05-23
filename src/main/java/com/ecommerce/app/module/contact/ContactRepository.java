package com.ecommerce.app.module.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.config.DBConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ContactRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ContactRepository.class);

    public ContactMessage insert(ContactMessage m) {
        String sql = """
                INSERT INTO contact_messages
                    (message_id, name, email, phone, subject, message, user_id, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING message_id, name, email, phone, subject, message, user_id, status, created_at, updated_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            UUID id = m.getMessageId() != null ? m.getMessageId() : UUID.randomUUID();
            ps.setObject(1, id);
            ps.setString(2, m.getName());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getPhone());
            ps.setString(5, m.getSubject());
            ps.setString(6, m.getMessage());
            ps.setObject(7, m.getUserId());
            ps.setString(8, m.getStatus() == null ? "NEW" : m.getStatus());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at contact insert ", e);
        }
        return null;
    }

    public List<ContactMessage> findAll(int limit, int offset, String statusFilter) {
        StringBuilder sb = new StringBuilder("""
                SELECT message_id, name, email, phone, subject, message, user_id, status, created_at, updated_at
                FROM contact_messages
                """);
        if (statusFilter != null && !statusFilter.isBlank()) {
            sb.append(" WHERE status = ? ");
        }
        sb.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

        List<ContactMessage> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            if (statusFilter != null && !statusFilter.isBlank()) {
                ps.setString(idx++, statusFilter);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findAll contact ", e);
        }
        return out;
    }

    public int countAll(String statusFilter) {
        String sql = (statusFilter != null && !statusFilter.isBlank())
                ? "SELECT COUNT(*) FROM contact_messages WHERE status = ?"
                : "SELECT COUNT(*) FROM contact_messages";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (statusFilter != null && !statusFilter.isBlank()) {
                ps.setString(1, statusFilter);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at countAll contact ", e);
        }
        return 0;
    }

    public boolean updateStatus(UUID messageId, String status) {
        String sql = "UPDATE contact_messages SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE message_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateStatus contact ", e);
        }
        return false;
    }

    public boolean delete(UUID messageId) {
        String sql = "DELETE FROM contact_messages WHERE message_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at delete contact ", e);
        }
        return false;
    }

    private ContactMessage mapRow(ResultSet rs) throws SQLException {
        ContactMessage m = new ContactMessage();
        m.setMessageId((UUID) rs.getObject("message_id"));
        m.setName(rs.getString("name"));
        m.setEmail(rs.getString("email"));
        m.setPhone(rs.getString("phone"));
        m.setSubject(rs.getString("subject"));
        m.setMessage(rs.getString("message"));
        m.setUserId((UUID) rs.getObject("user_id"));
        m.setStatus(rs.getString("status"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        m.setUpdatedAt(rs.getTimestamp("updated_at"));
        return m;
    }
}
