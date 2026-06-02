package com.ecommerce.app.module.iam.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.module.iam.config.DBConfig;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.models.UserStatus;
import com.ecommerce.app.module.iam.util.PasswordUtil;

import java.util.*;
import java.sql.*;

public class UserRepository {
    private static final Logger LOG = LoggerFactory.getLogger(UserRepository.class);

    /**
     * Sentinel stored in {@code password_hash} for accounts created via an
     * external identity provider (Google). It is intentionally NOT a valid
     * bcrypt hash (real hashes start with {@code $2}), so:
     *   - {@link PasswordUtil#verify} can never match it (favre-bcrypt returns
     *     false for a malformed hash rather than throwing), and
     *   - {@code AuthService.login} can detect it and tell the user to sign in
     *     with Google instead of returning a confusing "invalid password".
     * A later forgot-password reset simply overwrites it with a real hash,
     * upgrading the account to dual (Google + password) login.
     */
    public static final String OAUTH_PASSWORD_SENTINEL = "oauth:google";

    public User create(User user) {
        LOG.info("inside user repo");
        String sql = "insert into users (email,password_hash,first_name,last_name,status,is_admin,user_id) values (?,?,?,?,?,?,?)";
        UUID newid = UUID.randomUUID();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, user.getEmail());
            ps.setString(2, PasswordUtil.hash(user.getPasswordHash()));
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, UserStatus.ACTIVE.name());
            ps.setBoolean(6, false);
            ps.setObject(7, newid);

            ps.executeUpdate();
            user.setId(newid);
        } catch (SQLException e) {
            LOG.error("Sql exception at create user iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create user iam  ", e);
        }

        return user;
    }

    /**
     * Create a passwordless account for an external identity provider (Google).
     * Stores the OAuth sentinel verbatim in {@code password_hash} (no bcrypt)
     * and marks the email as verified, since Google has already proven it.
     * Returns null on failure (e.g. a race where the email now exists).
     */
    public User createOAuthUser(User user) {
        String sql = "insert into users (email,password_hash,first_name,last_name,status,is_admin,user_id,email_verified) "
                + "values (?,?,?,?,?,?,?,?)";
        UUID newid = UUID.randomUUID();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, user.getEmail());
            ps.setString(2, OAUTH_PASSWORD_SENTINEL); // literal sentinel, NOT hashed
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, UserStatus.ACTIVE.name());
            ps.setBoolean(6, false);
            ps.setObject(7, newid);
            ps.setBoolean(8, true); // Google already verified the email
            ps.executeUpdate();
            user.setId(newid);
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerified(true);
        } catch (SQLException e) {
            LOG.error("Sql exception at createOAuthUser iam  ", e);
            return null;
        } catch (Exception e) {
            LOG.error("unhandled exception at createOAuthUser iam  ", e);
            return null;
        }
        return user;
    }

    public User getUser(UUID userId) {
        String sql = "select * from users where user_id = ?";
        User user = new User();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                user.setId(rs.getObject("user_id", java.util.UUID.class));
                user.setEmail(rs.getString("email"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setStatus(UserStatus.valueOf(rs.getString("status")));
                try { user.setEmailVerified(rs.getBoolean("email_verified")); } catch (SQLException ignore) { }
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get user iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at get user iam  ", e);
        }
        return user;
    }

    public List<User> getAllUsers() {
        String sql = "select * from users";
        List<User> users = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User user = new User();

                 user.setId(rs.getObject("user_id", java.util.UUID.class));
                user.setEmail(rs.getString("email"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setStatus(UserStatus.valueOf(rs.getString("status")));
                try {
                    java.sql.Timestamp ll = rs.getTimestamp("last_login");
                    if (ll != null) user.setLastLogin(ll.toLocalDateTime());
                } catch (SQLException ignore) { }
                users.add(user);
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get user iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at get user iam  ", e);
        }

        return users;
    }

    public User getUserWithPassword(UUID userId) {
        String sql = "select * from users where user_id = ?";
        User user = new User();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                 user.setId(rs.getObject("user_id", java.util.UUID.class));
                user.setEmail(rs.getString("email"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setStatus(UserStatus.valueOf(rs.getString("status")));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get user iam with password ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at get user iam with password ", e);
        }
        return user;
    }

        public User getUserWithPassword(String email) {
        String sql = "select * from users where email = ?";
        User user = new User();
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, email );
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                 user.setId(rs.getObject("user_id", java.util.UUID.class));
                user.setEmail(rs.getString("email"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setStatus(UserStatus.valueOf(rs.getString("status")));
            }
        } catch (SQLException e) {
            LOG.error("Sql exception at get user iam with password ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at get user iam with password ", e);
        }
        return user;
    }

    public boolean updatePassword(UUID userId, String oldPassword, String newPassword) {
        String sql = "update users set password_hash = ? where user_id =?";
        User user = getUserWithPassword(userId);
        if (!PasswordUtil.verify(oldPassword,user.getPasswordHash())) {
            return false;
        }
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, PasswordUtil.hash(newPassword));
               ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Sql exception at update  user password iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  update  user password iam  ", e);
        }

        return false;
    }

    /**
     * Forgot-password reset: overwrite the password hash WITHOUT verifying an
     * old password. Identity is proven by possession of a valid, single-use
     * reset token (verified by the caller before this is invoked), so there is
     * no old password to check.
     */
    public boolean resetPassword(UUID userId, String newPassword) {
        String sql = "update users set password_hash = ? where user_id = ?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, PasswordUtil.hash(newPassword));
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Sql exception at resetPassword iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at resetPassword iam  ", e);
        }
        return false;
    }

    public boolean updateUserStatus(UUID userId,UserStatus status){
         String sql = "update users set status = ? where user_id =?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, status.name());
               ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("Sql exception at updateUserStatus iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at  updateUserStatus iam  ", e);
        }

        return false;
    }

    public void updateLastLogin(UUID userId) {
        String sql = "update users set last_login = NOW() where user_id = ?";
        try (Connection connection = DBConfig.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Sql exception at updateLastLogin iam  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateLastLogin iam  ", e);
        }
    }

}
