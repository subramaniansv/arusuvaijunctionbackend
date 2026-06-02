package com.ecommerce.app.module.iam.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.PasswordResetToken;

/**
 * Persistence for self-service password reset tokens. Only SHA-256 hashes of
 * the raw token are stored, so a DB leak can't reveal active reset links.
 * Mirrors {@link EmailVerificationRepository}.
 */
public class PasswordResetRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetRepository.class);

    /** Invalidate any outstanding (un-used, un-expired) tokens for a user so only one link is ever live. */
    public void invalidateOutstanding(UUID userId) {
        String sql = "UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("sql exception at invalidateOutstanding password reset ", e);
        }
    }

    public PasswordResetToken insert(PasswordResetToken t) {
        String sql = "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) "
                + "VALUES (?,?,?) RETURNING token_id, created_at";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, t.getUserId());
            ps.setString(2, t.getTokenHash());
            ps.setTimestamp(3, Timestamp.valueOf(t.getExpiresAt()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    t.setTokenId(rs.getObject("token_id", UUID.class));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) t.setCreatedAt(ts.toLocalDateTime());
                    return t;
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at insert password reset token ", e);
        }
        return null;
    }

    public PasswordResetToken findByHash(String tokenHash) {
        String sql = "SELECT token_id, user_id, token_hash, expires_at, used_at, created_at "
                + "FROM password_reset_tokens WHERE token_hash = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByHash password reset ", e);
        }
        return null;
    }

    /** Atomically consume a token (used_at goes from NULL to NOW). Returns true only on the first call. */
    public boolean markUsed(UUID tokenId) {
        String sql = "UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP "
                + "WHERE token_id = ? AND used_at IS NULL";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, tokenId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at markUsed password reset ", e);
        }
        return false;
    }

    private PasswordResetToken map(ResultSet rs) throws SQLException {
        PasswordResetToken t = new PasswordResetToken();
        t.setTokenId(rs.getObject("token_id", UUID.class));
        t.setUserId(rs.getObject("user_id", UUID.class));
        t.setTokenHash(rs.getString("token_hash"));
        Timestamp e = rs.getTimestamp("expires_at");
        if (e != null) t.setExpiresAt(e.toLocalDateTime());
        Timestamp u = rs.getTimestamp("used_at");
        if (u != null) t.setUsedAt(u.toLocalDateTime());
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) t.setCreatedAt(c.toLocalDateTime());
        return t;
    }

    /** Pure helper - exposed so callers can hash arbitrary tokens consistently. */
    public static String hash(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("sha-256 unavailable", e);
        }
    }

    /** Helper for the now-vs-expiry comparison without re-reading the row. */
    public static boolean isExpired(LocalDateTime expiresAt) {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }
}
