package com.ecommerce.app.module.address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.DBConfig;

import java.sql.*;
import java.util.*;

public class UserAddressRepository {
    private static final Logger LOG = LoggerFactory.getLogger(UserAddressRepository.class);

    // ------------------------------------------------------------------ list
    public List<UserAddress> findByUser(UUID userId) {
        String sql = "SELECT * FROM user_addresses WHERE user_id = ? ORDER BY is_default DESC, created_at ASC";
        List<UserAddress> list = new ArrayList<>();
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (Exception e) {
            LOG.error("findByUser error", e);
        }
        return list;
    }

    // ----------------------------------------------------------------- single
    public UserAddress findById(UUID addressId, UUID userId) {
        String sql = "SELECT * FROM user_addresses WHERE address_id = ? AND user_id = ?";
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, addressId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (Exception e) {
            LOG.error("findById error", e);
        }
        return null;
    }

    // ----------------------------------------------------------------- create
    public UserAddress create(UserAddress a) {
        // If this address is marked default, clear old default first
        if (a.isDefault()) clearDefault(a.getUserId(), null);

        String sql = """
            INSERT INTO user_addresses
              (user_id, label, full_name, phone, line1, line2,
               city, state, pincode, country, is_default)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            RETURNING *
            """;
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, a.getUserId());
            ps.setString(2, a.getLabel());
            ps.setString(3, a.getFullName());
            ps.setString(4, a.getPhone());
            ps.setString(5, a.getLine1());
            ps.setString(6, a.getLine2());
            ps.setString(7, a.getCity());
            ps.setString(8, a.getState());
            ps.setString(9, a.getPincode());
            ps.setString(10, a.getCountry());
            ps.setBoolean(11, a.isDefault());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (Exception e) {
            LOG.error("create address error", e);
        }
        return null;
    }

    // ----------------------------------------------------------------- update
    public UserAddress update(UserAddress a) {
        if (a.isDefault()) clearDefault(a.getUserId(), a.getAddressId());

        String sql = """
            UPDATE user_addresses
               SET label = ?, full_name = ?, phone = ?, line1 = ?, line2 = ?,
                   city = ?, state = ?, pincode = ?, country = ?,
                   is_default = ?, updated_at = now()
             WHERE address_id = ? AND user_id = ?
            RETURNING *
            """;
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, a.getLabel());
            ps.setString(2, a.getFullName());
            ps.setString(3, a.getPhone());
            ps.setString(4, a.getLine1());
            ps.setString(5, a.getLine2());
            ps.setString(6, a.getCity());
            ps.setString(7, a.getState());
            ps.setString(8, a.getPincode());
            ps.setString(9, a.getCountry());
            ps.setBoolean(10, a.isDefault());
            ps.setObject(11, a.getAddressId());
            ps.setObject(12, a.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (Exception e) {
            LOG.error("update address error", e);
        }
        return null;
    }

    // ----------------------------------------------------------------- delete
    public boolean delete(UUID addressId, UUID userId) {
        String sql = "DELETE FROM user_addresses WHERE address_id = ? AND user_id = ?";
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, addressId);
            ps.setObject(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("delete address error", e);
        }
        return false;
    }

    // ------------------------------------------------ clear default (private)
    private void clearDefault(UUID userId, UUID exceptId) {
        String sql = exceptId == null
            ? "UPDATE user_addresses SET is_default = FALSE WHERE user_id = ?"
            : "UPDATE user_addresses SET is_default = FALSE WHERE user_id = ? AND address_id <> ?";
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, userId);
            if (exceptId != null) ps.setObject(2, exceptId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.error("clearDefault error", e);
        }
    }

    // ------------------------------------------------------------------- map
    private UserAddress map(ResultSet rs) throws SQLException {
        UserAddress a = new UserAddress();
        a.setAddressId(rs.getObject("address_id", UUID.class));
        a.setUserId(rs.getObject("user_id", UUID.class));
        a.setLabel(rs.getString("label"));
        a.setFullName(rs.getString("full_name"));
        a.setPhone(rs.getString("phone"));
        a.setLine1(rs.getString("line1"));
        a.setLine2(rs.getString("line2"));
        a.setCity(rs.getString("city"));
        a.setState(rs.getString("state"));
        a.setPincode(rs.getString("pincode"));
        a.setCountry(rs.getString("country"));
        a.setDefault(rs.getBoolean("is_default"));
        a.setCreatedAt(rs.getTimestamp("created_at"));
        a.setUpdatedAt(rs.getTimestamp("updated_at"));
        return a;
    }
}
