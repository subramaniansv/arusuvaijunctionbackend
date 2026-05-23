package com.ecommerce.app.module.wishlist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.config.DBConfig;
import com.ecommerce.app.module.product.Product;

/**
 * Wishlist persistence.
 *
 * Table layout: wishlist_items(user_id, product_id, created_at)
 * with a composite PK on (user_id, product_id), so duplicate inserts
 * are a no-op via ON CONFLICT DO NOTHING.
 *
 * The list-view query joins wishlist_items -> products and pulls the
 * primary image URL + review aggregate via LATERAL subqueries, mirroring
 * the catalog list view so the frontend can render the same product
 * cards verbatim.
 */
public class WishlistRepository {
    private static final Logger LOG = LoggerFactory.getLogger(WishlistRepository.class);

    /** Returns true if the row was newly inserted, false if it already existed. */
    public boolean add(UUID userId, UUID productId) {
        String sql = """
                INSERT INTO wishlist_items (user_id, product_id)
                VALUES (?, ?)
                ON CONFLICT (user_id, product_id) DO NOTHING
                """;
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist add  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist add  ", e);
        }
        return false;
    }

    /** Returns true if a row was actually removed. */
    public boolean remove(UUID userId, UUID productId) {
        String sql = "DELETE FROM wishlist_items WHERE user_id = ? AND product_id = ?";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist remove  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist remove  ", e);
        }
        return false;
    }

    public boolean exists(UUID userId, UUID productId) {
        String sql = "SELECT 1 FROM wishlist_items WHERE user_id = ? AND product_id = ? LIMIT 1";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist exists  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist exists  ", e);
        }
        return false;
    }

    /** Lightweight ID-only fetch, used by the frontend to mark cards. */
    public List<UUID> findProductIds(UUID userId) {
        String sql = "SELECT product_id FROM wishlist_items WHERE user_id = ? ORDER BY created_at DESC";
        List<UUID> ids = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject("product_id", UUID.class));
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist findProductIds  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist findProductIds  ", e);
        }
        return ids;
    }

    /**
     * Full list view: wishlist rows joined to products + primary image +
     * review aggregate. Inactive products and rows where the underlying
     * product was deleted are filtered out at the SQL level.
     */
    public List<WishlistItem> findAllForUser(UUID userId) {
        String sql = """
                SELECT w.user_id, w.product_id, w.created_at AS wishlist_added_at,
                       p.product_id   AS p_product_id,
                       p.name, p.description, p.category, p.ingredients,
                       p.price, p.stock_quantity, p.is_active,
                       p.created_at   AS p_created_at,
                       p.updated_at   AS p_updated_at,
                       pi.image_url   AS primary_image_url,
                       COALESCE(r.avg_rating, 0)   AS avg_rating,
                       COALESCE(r.review_count, 0) AS review_count
                FROM wishlist_items w
                JOIN products p ON p.product_id = w.product_id AND p.is_active = true
                LEFT JOIN LATERAL (
                    SELECT image_url FROM product_images
                    WHERE product_id = p.product_id AND is_primary = true
                    LIMIT 1
                ) pi ON true
                LEFT JOIN LATERAL (
                    SELECT AVG(rating)::float AS avg_rating,
                           COUNT(*)          AS review_count
                    FROM reviews
                    WHERE product_id = p.product_id
                ) r ON true
                WHERE w.user_id = ?
                ORDER BY w.created_at DESC
                """;

        List<WishlistItem> items = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WishlistItem item = new WishlistItem();
                    item.setUserId(rs.getObject("user_id", UUID.class));
                    item.setProductId(rs.getObject("product_id", UUID.class));
                    item.setCreatedAt(rs.getTimestamp("wishlist_added_at"));

                    Product p = new Product();
                    p.setId(rs.getObject("p_product_id", UUID.class));
                    p.setName(rs.getString("name"));
                    p.setDescription(rs.getString("description"));
                    p.setCategory(rs.getString("category"));
                    p.setIngredients(rs.getString("ingredients"));
                    p.setPrice(rs.getDouble("price"));
                    p.setStockQuantity(rs.getInt("stock_quantity"));
                    p.setActive(rs.getBoolean("is_active"));
                    p.setCreatedAt(rs.getTimestamp("p_created_at"));
                    p.setUpdatedAt(rs.getTimestamp("p_updated_at"));
                    p.setPrimaryImageUrl(rs.getString("primary_image_url"));
                    double avg = rs.getDouble("avg_rating");
                    p.setAverageRating(Math.round(avg * 100.0) / 100.0);
                    p.setReviewCount(rs.getInt("review_count"));
                    item.setProduct(p);

                    items.add(item);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist findAllForUser  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist findAllForUser  ", e);
        }
        return items;
    }

    public int countForUser(UUID userId) {
        String sql = "SELECT COUNT(*) FROM wishlist_items WHERE user_id = ?";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at wishlist countForUser  ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at wishlist countForUser  ", e);
        }
        return 0;
    }
}
