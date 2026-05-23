package com.ecommerce.app.module.product;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC repository for {@link ProductVariant}. Reads return active
 * variants ordered by sort_order then label so the storefront can
 * render the chip picker in a stable order. All writes operate on
 * a passed-in {@link Connection} so they can join the caller's
 * transaction (used during checkout to decrement variant stock
 * atomically with order creation).
 */
public class ProductVariantRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ProductVariantRepository.class);

    public List<ProductVariant> findByProductId(UUID productId) {
        return findByProductId(productId, false);
    }

    /**
     * Variant list for a product. With {@code includeInactive=false}
     * (storefront) only active variants are returned. With
     * {@code includeInactive=true} (admin) every variant is returned so
     * staff can re-activate soft-deleted entries.
     */
    public List<ProductVariant> findByProductId(UUID productId, boolean includeInactive) {
        String sql = """
                SELECT variant_id, product_id, label, price, stock_quantity,
                       is_active, sort_order, created_at, updated_at
                FROM product_variants
                WHERE product_id = ?
                """
                + (includeInactive ? "" : " AND is_active = true ")
                + " ORDER BY sort_order, label";
        List<ProductVariant> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByProductId variant ", e);
        }
        return out;
    }

    public ProductVariant findById(UUID variantId) {
        String sql = """
                SELECT variant_id, product_id, label, price, stock_quantity,
                       is_active, sort_order, created_at, updated_at
                FROM product_variants
                WHERE variant_id = ?
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, variantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findById variant ", e);
        }
        return null;
    }

    /**
     * Decrement variant stock only if enough is available. Returns true
     * when exactly one row was updated; false means the variant was
     * deleted, inactive, or had insufficient stock. Caller MUST react
     * (rollback) when this returns false.
     */
    public boolean decrementStock(Connection connection, UUID variantId, int quantity) {
        String sql = """
                UPDATE product_variants
                   SET stock_quantity = stock_quantity - ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE variant_id = ? AND stock_quantity >= ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setObject(2, variantId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at decrementStock variant ", e);
        }
        return false;
    }

    /**
     * Add stock back to a variant — used when an order is cancelled.
     */
    public boolean incrementStock(Connection connection, UUID variantId, int quantity) {
        String sql = """
                UPDATE product_variants
                   SET stock_quantity = stock_quantity + ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE variant_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setObject(2, variantId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at incrementStock variant ", e);
        }
        return false;
    }

    private ProductVariant mapRow(ResultSet rs) throws SQLException {
        ProductVariant v = new ProductVariant();
        v.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
        v.setProductId(rs.getObject("product_id", java.util.UUID.class));
        v.setLabel(rs.getString("label"));
        v.setPrice(rs.getDouble("price"));
        v.setStockQuantity(rs.getInt("stock_quantity"));
        v.setActive(rs.getBoolean("is_active"));
        v.setSortOrder(rs.getInt("sort_order"));
        v.setCreatedAt(rs.getTimestamp("created_at"));
        v.setUpdatedAt(rs.getTimestamp("updated_at"));
        return v;
    }

    // ---------------------------------------------------------------
    // Admin CRUD
    // ---------------------------------------------------------------

    /**
     * Insert a new variant. {@code variantId} and timestamps are filled
     * server-side; {@code productId}, {@code label}, {@code price} are
     * required. Returns the row as it was persisted (with the generated
     * UUID) or {@code null} on failure.
     */
    public ProductVariant create(ProductVariant v) {
        String sql = """
                INSERT INTO product_variants
                    (product_id, label, price, stock_quantity, is_active, sort_order)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING variant_id, product_id, label, price, stock_quantity,
                          is_active, sort_order, created_at, updated_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, v.getProductId());
            ps.setString(2, v.getLabel());
            ps.setDouble(3, v.getPrice());
            ps.setInt(4, Math.max(0, v.getStockQuantity()));
            ps.setBoolean(5, v.isActive());
            ps.setInt(6, v.getSortOrder());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at create variant ", e);
        }
        return null;
    }

    /**
     * Full-update of a variant by id. Only mutable fields are touched —
     * {@code productId} and {@code variantId} cannot be moved. Returns
     * the post-update row or {@code null} when no row matched.
     */
    public ProductVariant update(ProductVariant v) {
        String sql = """
                UPDATE product_variants SET
                    label = ?,
                    price = ?,
                    stock_quantity = ?,
                    is_active = ?,
                    sort_order = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE variant_id = ?
                RETURNING variant_id, product_id, label, price, stock_quantity,
                          is_active, sort_order, created_at, updated_at
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, v.getLabel());
            ps.setDouble(2, v.getPrice());
            ps.setInt(3, Math.max(0, v.getStockQuantity()));
            ps.setBoolean(4, v.isActive());
            ps.setInt(5, v.getSortOrder());
            ps.setObject(6, v.getVariantId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at update variant ", e);
        }
        return null;
    }

    /**
     * Hard-delete a variant. Returns {@code true} when one row was removed.
     * Callers can choose soft-deletion instead by calling {@link #update}
     * with {@code isActive=false}.
     */
    public boolean delete(UUID variantId) {
        String sql = "DELETE FROM product_variants WHERE variant_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, variantId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at delete variant ", e);
        }
        return false;
    }
}
