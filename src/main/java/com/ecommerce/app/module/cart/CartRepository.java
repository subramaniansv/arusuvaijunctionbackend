package com.ecommerce.app.module.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.UUID;

import com.ecommerce.app.config.DBConfig;

public class CartRepository {
    private static final Logger LOG = LoggerFactory.getLogger(CartRepository.class);


    public Cart create(Connection connection, Cart cart) {
        String sql = "INSERT INTO carts (cart_id, user_id, total_amount) VALUES (?,?,?)";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setObject(2, cart.getUserId());
            ps.setDouble(3, 0.0);
            ps.executeUpdate();
            cart.setCartId(id);
            cart.setTotalAmount(0.0);
        } catch (SQLException e) {
            LOG.error("sql exception at create cart  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create cart  ", e);
        }
        return cart;
    }

    public Cart findByUserId(UUID userId) {
        String sql = """
                SELECT cart_id, user_id, total_amount, created_at, updated_at
                FROM carts
                WHERE user_id = ?
                LIMIT 1
                """;
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Cart cart = new Cart();
                cart.setCartId(rs.getObject("cart_id", java.util.UUID.class));
                cart.setUserId(rs.getObject("user_id", java.util.UUID.class));
                cart.setTotalAmount(rs.getDouble("total_amount"));
                cart.setCreatedAt(rs.getTimestamp("created_at"));
                cart.setUpdatedAt(rs.getTimestamp("updated_at"));
                return cart;
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByUserId cart  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByUserId cart  ", e);
        }
        return null;
    }

    public boolean updateTotal(Connection connection, UUID cartId, double total) {
        String sql = "UPDATE carts SET total_amount = ? WHERE cart_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setDouble(1, total);
            ps.setObject(2, cartId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateTotal cart  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateTotal cart  ", e);
        }
        return false;
    }

    public boolean deleteByUserId(UUID userId) {
        String sql = "DELETE FROM carts WHERE user_id = ?";
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at deleteByUserId cart  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at deleteByUserId cart  ", e);
        }
        return false;
    }
}
