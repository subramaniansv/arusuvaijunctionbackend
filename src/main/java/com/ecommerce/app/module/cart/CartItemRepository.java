package com.ecommerce.app.module.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ecommerce.app.config.DBConfig;

public class CartItemRepository {
    private static final Logger LOG = LoggerFactory.getLogger(CartItemRepository.class);


    public CartItem create(Connection connection, CartItem item) {
        String sql = "INSERT INTO cart_items (cart_item_id, cart_id, product_id, variant_id, variant_label, quantity, price) "
                + "VALUES (?,?,?,?,?,?,?)";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            UUID id = UUID.randomUUID();
            ps.setObject(1, id);
            ps.setObject(2, item.getCartId());
            ps.setObject(3, item.getProductId());
            ps.setObject(4, item.getVariantId());
            ps.setString(5, item.getVariantLabel());
            ps.setInt(6, item.getQuantity());
            ps.setDouble(7, item.getPrice());
            ps.executeUpdate();
            item.setCartItemId(id);
        } catch (SQLException e) {
            LOG.error("sql exception at create cart item  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create cart item  ", e);
        }
        return item;
    }

    /**
     * Look up an existing cart row for the (cart, product, variant) triple.
     * variantId may be null for products without variants - the SQL uses
     * IS NOT DISTINCT FROM so the same query handles both cases and a
     * product+"250g" line doesn't collide with the product+"500g" line.
     */
    public CartItem findByCartIdAndProductVariant(UUID cartId, UUID productId, UUID variantId) {
        String sql = """
                SELECT cart_item_id, cart_id, product_id, variant_id, variant_label, quantity, price
                FROM cart_items
                WHERE cart_id = ? AND product_id = ?
                  AND variant_id IS NOT DISTINCT FROM ?
                LIMIT 1
                """;
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, cartId);
            ps.setObject(2, productId);
            ps.setObject(3, variantId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                CartItem item = new CartItem();
                item.setCartItemId(rs.getObject("cart_item_id", java.util.UUID.class));
                item.setCartId(rs.getObject("cart_id", java.util.UUID.class));
                item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                item.setVariantLabel(rs.getString("variant_label"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getDouble("price"));
                return item;
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByCartIdAndProductVariant  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByCartIdAndProductVariant  ", e);
        }
        return null;
    }

    public List<CartItem> findByCartIdWithProductDetails(UUID cartId) {
        String sql = """
                SELECT
                    ci.cart_item_id,
                    ci.cart_id,
                    ci.product_id,
                    ci.variant_id,
                    ci.variant_label,
                    ci.quantity,
                    ci.price,
                    p.name AS product_name,
                    (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.product_id AND pi.is_primary = true
                        LIMIT 1) AS image_url
                FROM cart_items ci
                JOIN products p ON ci.product_id = p.product_id
                WHERE ci.cart_id = ?
                """;
        Map<UUID, CartItem> map = new LinkedHashMap<>();
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, cartId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CartItem item = new CartItem();
                item.setCartItemId(rs.getObject("cart_item_id", java.util.UUID.class));
                item.setCartId(rs.getObject("cart_id", java.util.UUID.class));
                item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                item.setVariantLabel(rs.getString("variant_label"));
                item.setQuantity(rs.getInt("quantity"));
                item.setPrice(rs.getDouble("price"));
                item.setProductName(rs.getString("product_name"));
                item.setImageUrl(rs.getString("image_url"));
                item.setSubtotal(item.getPrice() * item.getQuantity());
                map.put(item.getCartItemId(), item);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByCartIdWithProductDetails  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByCartIdWithProductDetails  ", e);
        }
        return new ArrayList<>(map.values());
    }

    public boolean updateQuantityAndPrice(Connection connection, UUID cartItemId, int quantity, double price) {
        String sql = "UPDATE cart_items SET quantity = ?, price = ? WHERE cart_item_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setInt(1, quantity);
            ps.setDouble(2, price);
            ps.setObject(3, cartItemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateQuantityAndPrice  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateQuantityAndPrice  ", e);
        }
        return false;
    }

    public boolean deleteByCartItemId(Connection connection, UUID cartItemId) {
        String sql = "DELETE FROM cart_items WHERE cart_item_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, cartItemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at deleteByCartItemId  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at deleteByCartItemId  ", e);
        }
        return false;
    }

    public boolean deleteByCartId(Connection connection, UUID cartId) {
        String sql = "DELETE FROM cart_items WHERE cart_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, cartId);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            LOG.error("sql exception at deleteByCartId  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at deleteByCartId  ", e);
        }
        return false;
    }

    public double computeTotal(Connection connection, UUID cartId) {
        String sql = "SELECT COALESCE(SUM(quantity * price), 0) AS total FROM cart_items WHERE cart_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setObject(1, cartId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("total");
            }
        } catch (SQLException e) {
            LOG.error("sql exception at computeTotal  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at computeTotal  ", e);
        }
        return 0.0;
    }
}
