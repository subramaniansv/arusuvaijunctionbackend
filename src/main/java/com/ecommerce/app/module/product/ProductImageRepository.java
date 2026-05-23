package com.ecommerce.app.module.product;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.config.DBConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProductImageRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ProductImageRepository.class);


    // Create image
    public ProductImage create(ProductImage image) {
        String sql = """
                INSERT INTO product_images
                (
                    product_image_id,
                    product_id,
                    image_url,
                    object_key,
                    is_primary
                )
                VALUES (?,?,?,?,?)
                """;
        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {

            UUID imageId = UUID.randomUUID();

            ps.setObject(1, imageId);
            ps.setObject(2, image.getProductId());
            ps.setString( 3,image.getImageUrl());
            ps.setString( 4,image.getObjectKey() );
            ps.setBoolean(5,image.isPrimary());
            ps.executeUpdate();
            image.setId(imageId);
            return image;
        } catch (SQLException e) {
            LOG.error("sql exception at create image ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create image ", e);
        }
        return null;
    }

    // Find images by product id
    public List<ProductImage> findByProductId(UUID productId) {

        String sql = """
                SELECT *
                FROM product_images
                WHERE product_id = ?
                ORDER BY is_primary DESC
                """;

        List<ProductImage> images =new ArrayList<>();

        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps =connection.prepareStatement(sql)
        ) {
            ps.setObject(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                images.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByProductId ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByProductId ", e);
        }
        return images;
    }

    // Find primary image
    public ProductImage findPrimaryImage(UUID productId) {

        String sql = """
                SELECT *
                FROM product_images
                WHERE product_id = ?
                AND is_primary = true
                LIMIT 1
                """;

        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {

            ps.setObject(1, productId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findPrimaryImage ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findPrimaryImage ", e);
        }
        return null;
    }

    /**
     * Batch lookup: given a list of product ids, return a map of
     * {@code productId -> primary image_url}. Products with no rows in
     * product_images are simply absent from the returned map.
     *
     * <p>This avoids the N+1 query pattern when hydrating product lists
     * (catalog, search, recommendations) with thumbnails: callers do one
     * call to {@code ProductRepository.findAll/search/...} and one call
     * here, instead of one image query per product.</p>
     */
    public Map<UUID, String> findPrimaryUrlsByProductIds(Collection<UUID> productIds) {
        Map<UUID, String> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return result;
        }
        // Build "?, ?, ?" placeholder list dynamically. All values are
        // bound via setObject(UUID) so this is safe from SQL injection.
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < productIds.size(); i++) {
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
        }
        String sql = "SELECT product_id, image_url FROM product_images "
                + "WHERE is_primary = true AND product_id IN (" + placeholders + ")";

        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {
            int idx = 1;
            for (UUID id : productIds) {
                ps.setObject(idx++, id);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.put(
                        rs.getObject("product_id", java.util.UUID.class),
                        rs.getString("image_url"));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findPrimaryUrlsByProductIds ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findPrimaryUrlsByProductIds ", e);
        }
        return result;
    }

    // Find a single image by its id. Used by the admin image controller
    // so we can look up the R2 object key before deleting.
    public ProductImage findById(UUID imageId) {
        String sql = "SELECT * FROM product_images WHERE product_image_id = ?";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findById image ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findById image ", e);
        }
        return null;
    }

    // Delete image by id
    public boolean deleteById(UUID imageId) {
        String sql = """
                DELETE FROM product_images
                WHERE product_image_id = ?
                """;
        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {
            ps.setObject(1, imageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at deleteById ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at deleteById ", e);
        }
        return false;
    }

    // Promote a single image to primary, clearing primary on all
    // other rows for the same product. Atomic via a single connection.
    public boolean setPrimary(UUID productId, UUID imageId) {
        try (Connection c = DBConfig.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement clear = c.prepareStatement(
                    "UPDATE product_images SET is_primary=false WHERE product_id=?");
                 PreparedStatement set = c.prepareStatement(
                    "UPDATE product_images SET is_primary=true WHERE product_image_id=? AND product_id=?")) {
                clear.setObject(1, productId);
                clear.executeUpdate();
                set.setObject(1, imageId);
                set.setObject(2, productId);
                int n = set.executeUpdate();
                c.commit();
                return n == 1;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at setPrimary ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at setPrimary ", e);
        }
        return false;
    }

    // Delete all images of product
    public boolean deleteByProductId(UUID productId) {

        String sql = """
                DELETE FROM product_images
                WHERE product_id = ?
                """;

        try (
                Connection connection = DBConfig.getConnection();
                PreparedStatement ps =connection.prepareStatement(sql)
        ) {

            ps.setObject(1, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at deleteByProductId ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at deleteByProductId ", e);
        }
        return false;
    }

    // Map resultset to object
    private ProductImage mapRow(ResultSet rs)
            throws SQLException {

        ProductImage image = new ProductImage();
        image.setId(rs.getObject("product_image_id", java.util.UUID.class) );

        image.setProductId(rs.getObject("product_id", java.util.UUID.class) );

        image.setImageUrl(rs.getString("image_url"));

        image.setObjectKey( rs.getString("object_key"));

        image.setPrimary( rs.getBoolean("is_primary"));

        image.setCreatedAt(rs.getTimestamp("created_at"));

        return image;
    }
}