package com.ecommerce.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.RefreshToken;

public class AuthRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AuthRepository.class);

    public RefreshToken create(RefreshToken refreshToken) {
        String sql = "insert into refresh_token (token_hash,user_id,ip_address,user_agent,expires_at) values(?,?,?,?,?)";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, refreshToken.getTokenHash());
            ps.setObject(2, refreshToken.getUserId());
            ps.setString(3, refreshToken.getIpAddress());
            ps.setString(4, refreshToken.getUserAgent());
            Timestamp expiresAt = Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS));
            ps.setTimestamp(5, expiresAt);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                refreshToken.setId(rs.getLong(1));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at auth create iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth create iam  ", e);
        }
        return refreshToken;
    }

    public RefreshToken getRefreshTokenByTokenHash(String tokenHash) {
        String sql = "select * from refresh_token where token_hash =? and is_revoked = false ";
        RefreshToken refreshToken = new RefreshToken();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, tokenHash);
            // ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                refreshToken.setId(rs.getLong("refresh_token_id"));
                refreshToken.setTokenHash(rs.getString("token_hash"));
                refreshToken.setUserId(rs.getObject("user_id", java.util.UUID.class));
                refreshToken.setCreatedAt(rs.getTimestamp("created_at").getTime());
                refreshToken.setIpAddress(rs.getString("ip_address"));
                refreshToken.setUserAgent(rs.getString("user_agent"));
                refreshToken.setexpiredAt(rs.getTimestamp("expires_at").getTime());
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at auth get refresh iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth get refresh  iam  ", e);
        }
        return refreshToken;
    }

    public List<RefreshToken> getAllRefreshTokenByUserId(UUID userId) {
        String sql = "select * from refresh_token where user_id =? and is_revoked = false and expires_at > ? ";
        List<RefreshToken> tokens = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                RefreshToken refreshToken = new RefreshToken();

                refreshToken.setId(rs.getLong("refresh_token_id"));
                refreshToken.setTokenHash(rs.getString("token_hash"));
                refreshToken.setUserId(rs.getObject("user_id", java.util.UUID.class));
                refreshToken.setCreatedAt(rs.getTimestamp("created_at").getTime());
                refreshToken.setIpAddress(rs.getString("ip_address"));
                refreshToken.setUserAgent(rs.getString("user_agent"));
                refreshToken.setexpiredAt(rs.getTimestamp("expires_at").getTime());
                tokens.add(refreshToken);
            }

        } catch (SQLException e) {
            LOG.error("Sql exception at auth get refresh iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth get refresh  iam  ", e);
        }
        return tokens;
    }

    public void revokeByTokenHash(String tokenHash,UUID userid){
        String sql ="update refresh_token set is_revoked = true where token_hash =? and user_id = ?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, tokenHash);
            ps.setObject(2, userid);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at authrevokeByTokenHash iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth revokeByTokenHash  iam  ", e);
        }
    }
    public void revokeByUserId(UUID userId){
        String sql ="update refresh_token set is_revoked = true where user_id =?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at authrevokeByuserId iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth revokeByTokenHash  iam  ", e);
        }
    }
    public void deleteExpiredTokens(){
        String sql = "delete from refresh_token where expires_at < ?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at deleteExpiredTokens iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  auth deleteExpiredTokens  iam  ", e);
        }
    }

}
