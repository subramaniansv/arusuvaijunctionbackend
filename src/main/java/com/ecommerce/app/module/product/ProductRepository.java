package com.ecommerce.app.module.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.config.DBConfig;

import java.sql.*;
import java.util.*;

public class ProductRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ProductRepository.class);


    // Create product
    public Product create(Product product) {

        String sql = """
                INSERT INTO products
                (
                    product_id,
                    name,
                    description,
                    category,
                    ingredients,
                    name_tamil,
                    description_tamil,
                    ingredients_tamil,
                    price,
                    stock_quantity,
                    is_active
                )
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            UUID productId = UUID.randomUUID();

            ps.setObject(1, productId);
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setString(4, product.getCategory());
            ps.setString(5, product.getIngredients());
            ps.setString(6, emptyToNull(product.getNameTamil()));
            ps.setString(7, emptyToNull(product.getDescriptionTamil()));
            ps.setString(8, emptyToNull(product.getIngredientsTamil()));
            ps.setDouble(9, product.getPrice());
            ps.setInt(10, product.getStockQuantity());
            ps.setBoolean(11, product.isActive());

            ps.executeUpdate();

            product.setId(productId);

            return product;

        } catch (SQLException e) {
            LOG.error("sql exception at create product ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create product ", e);
        }

        return null;
    }

    // Find product by id
    public Product findById(UUID productId) {

        String sql = """
                SELECT * FROM products
                WHERE product_id = ?
                LIMIT 1
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, productId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapRow(rs);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findById ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findById ", e);
        }

        return null;
    }

    // Get all products
    public List<Product> findAll(int limit, int offset) {

        String sql = """
                SELECT * FROM products
                WHERE is_active = true
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findAll ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findAll ", e);
        }

        return products;
    }

    // List-view catalog query.
    //
    // Single-shot replacement for findAll + findPrimaryUrlsByProductIds +
    // summariesForProducts. Collapses 3 sequential Neon round-trips into
    // one by using LATERAL subqueries for the primary image URL and the
    // review aggregate. Indexes: products(created_at) for ORDER BY,
    // idx_product_images_product_id_primary and idx_reviews_product carry
    // the lateral lookups.
    public List<Product> findAllListView(int limit, int offset) {

        String sql = """
                SELECT p.product_id, p.name, p.description, p.category,
                       p.ingredients, p.name_tamil, p.description_tamil,
                       p.ingredients_tamil,
                       p.price, p.stock_quantity, p.is_active,
                       p.created_at, p.updated_at,
                       pi.image_url AS primary_image_url,
                       COALESCE(r.avg_rating, 0) AS avg_rating,
                       COALESCE(r.review_count, 0) AS review_count
                FROM products p
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
                ORDER BY p.created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Product p = mapRow(rs);
                p.setPrimaryImageUrl(rs.getString("primary_image_url"));
                double avg = rs.getDouble("avg_rating");
                p.setAverageRating(Math.round(avg * 100.0) / 100.0);
                p.setReviewCount(rs.getInt("review_count"));
                products.add(p);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findAllListView ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findAllListView ", e);
        }

        return products;
    }

    /** Same as findAllListView but restricts to active products only (public catalog). */
    public List<Product> findAllListViewActive(int limit, int offset) {
        String sql = """
                SELECT p.product_id, p.name, p.description, p.category,
                       p.ingredients, p.name_tamil, p.description_tamil,
                       p.ingredients_tamil,
                       p.price, p.stock_quantity, p.is_active,
                       p.created_at, p.updated_at,
                       pi.image_url AS primary_image_url,
                       COALESCE(r.avg_rating, 0) AS avg_rating,
                       COALESCE(r.review_count, 0) AS review_count
                FROM products p
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
                WHERE p.is_active = true
                ORDER BY p.created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Product p = mapRow(rs);
                p.setPrimaryImageUrl(rs.getString("primary_image_url"));
                double avg = rs.getDouble("avg_rating");
                p.setAverageRating(Math.round(avg * 100.0) / 100.0);
                p.setReviewCount(rs.getInt("review_count"));
                products.add(p);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findAllListViewActive ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findAllListViewActive ", e);
        }

        return products;
    }

    // Category-filter catalog query (list view).
    //
    // Single-shot query for the common "filter by category" path. Mirrors
    // findAllListView but adds an exact-match WHERE on category. Exact
    // equality lets Postgres use idx_products_category /
    // idx_products_category_created; ILIKE (which the generic search()
    // uses) cannot use those btree indexes. Category values come from
    // findDistinctCategories so they are already normalised.
    //
    // sort: "newest" (default) | "price_asc" | "price_desc"
    public List<Product> findByCategoriesListView(
            List<String> categories, String sort, int limit, int offset) {

        if (categories == null || categories.isEmpty()) {
            return findAllListView(limit, offset);
        }

        // Case-insensitive OR of ILIKE per category. ILIKE with no
        // wildcards behaves as case-insensitive equality, which is what
        // we want here: callers (Home page card links, deep links, etc.)
        // may pass `sweets` while the DB stores `Sweets`. Using ILIKE
        // keeps that working without forcing every caller to know the
        // canonical casing.
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) placeholders.append(" OR ");
            placeholders.append("p.category ILIKE ?");
        }

        String orderBy;
        String s = sort == null ? "" : sort.trim().toLowerCase();
        if ("price_asc".equals(s)) {
            orderBy = "ORDER BY p.price ASC, p.created_at DESC";
        } else if ("price_desc".equals(s)) {
            orderBy = "ORDER BY p.price DESC, p.created_at DESC";
        } else {
            orderBy = "ORDER BY p.created_at DESC";
        }

        String sql =
                "SELECT p.product_id, p.name, p.description, p.category, "
              + "       p.ingredients, p.name_tamil, p.description_tamil, "
              + "       p.ingredients_tamil, "
              + "       p.price, p.stock_quantity, p.is_active, "
              + "       p.created_at, p.updated_at, "
              + "       pi.image_url AS primary_image_url, "
              + "       COALESCE(r.avg_rating, 0) AS avg_rating, "
              + "       COALESCE(r.review_count, 0) AS review_count "
              + "FROM products p "
              + "LEFT JOIN LATERAL ( "
              + "    SELECT image_url FROM product_images "
              + "    WHERE product_id = p.product_id AND is_primary = true "
              + "    LIMIT 1 "
              + ") pi ON true "
              + "LEFT JOIN LATERAL ( "
              + "    SELECT AVG(rating)::float AS avg_rating, COUNT(*) AS review_count "
              + "    FROM reviews WHERE product_id = p.product_id "
              + ") r ON true "
              + "WHERE p.is_active = true AND (" + placeholders + ") "
              + orderBy + " "
              + "LIMIT ? OFFSET ?";

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            int idx = 1;
            for (String c : categories) {
                ps.setString(idx++, c);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Product p = mapRow(rs);
                p.setPrimaryImageUrl(rs.getString("primary_image_url"));
                double avg = rs.getDouble("avg_rating");
                p.setAverageRating(Math.round(avg * 100.0) / 100.0);
                p.setReviewCount(rs.getInt("review_count"));
                products.add(p);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findByCategoriesListView ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByCategoriesListView ", e);
        }

        return products;
    }

    // Find products by category
    public List<Product> findByCategory(
            String category,
            int limit,
            int offset
    ) {

        String sql = """
                SELECT * FROM products
                WHERE category ILIKE ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, category);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findByCategory ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findByCategory ", e);
        }

        return products;
    }

    // Update product
    public Product update(Product product) {

        String sql = """
                UPDATE products
                SET
                    name = ?,
                    description = ?,
                    category = ?,
                    ingredients = ?,
                    name_tamil = ?,
                    description_tamil = ?,
                    ingredients_tamil = ?,
                    price = ?,
                    stock_quantity = ?,
                    is_active = ?
                WHERE product_id = ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, product.getName());
            ps.setString(2, product.getDescription());
            ps.setString(3, product.getCategory());
            ps.setString(4, product.getIngredients());
            ps.setString(5, emptyToNull(product.getNameTamil()));
            ps.setString(6, emptyToNull(product.getDescriptionTamil()));
            ps.setString(7, emptyToNull(product.getIngredientsTamil()));
            ps.setDouble(8, product.getPrice());
            ps.setInt(9, product.getStockQuantity());
            ps.setBoolean(10, product.isActive());
            ps.setObject(11, product.getId());

            int rows = ps.executeUpdate();

            if (rows > 0) {
                return product;
            }

        } catch (SQLException e) {
            LOG.error("sql exception at update ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at update ", e);
        }

        return null;
    }

    // Delete product
    public boolean delete(UUID productId) {

        String sql = """
                DELETE FROM products
                WHERE product_id = ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, productId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at delete ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at delete ", e);
        }

        return false;
    }

    // Check product exists by id
    public boolean existsById(UUID productId) {

        String sql = """
                SELECT 1 FROM products
                WHERE product_id = ?
                LIMIT 1
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, productId);

            ResultSet rs = ps.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            LOG.error("sql exception at existsById ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at existsById ", e);
        }

        return false;
    }

    // Check product exists by name
    public boolean existsByName(String name) {

        String sql = """
                SELECT 1 FROM products
                WHERE name = ?
                LIMIT 1
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, name);

            ResultSet rs = ps.executeQuery();

            return rs.next();

        } catch (SQLException e) {
            LOG.error("sql exception at existsByName ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at existsByName ", e);
        }

        return false;
    }

    // Update stock directly
    public boolean updateStock(UUID productId, int newStock) {

        String sql = """
                UPDATE products
                SET stock_quantity = ?
                WHERE product_id = ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, newStock);
            ps.setObject(2, productId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at updateStock ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateStock ", e);
        }

        return false;
    }

    // Increase stock
    public boolean incrementStock(UUID productId, int quantity) {

        String sql = """
                UPDATE products
                SET stock_quantity = stock_quantity + ?
                WHERE product_id = ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, quantity);
            ps.setObject(2, productId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at incrementStock ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at incrementStock ", e);
        }

        return false;
    }

    // Transactional overload — caller controls the connection so the stock
    // increment commits/rolls back with the rest of their work (e.g. order
    // cancellation must release stock atomically with the status flip).
    public boolean incrementStock(Connection connection, UUID productId, int quantity) {

        String sql = """
                UPDATE products
                SET stock_quantity = stock_quantity + ?
                WHERE product_id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, quantity);
            ps.setObject(2, productId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at incrementStock(tx) ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at incrementStock(tx) ", e);
        }

        return false;
    }

    // Decrease stock safely
    // IMPORTANT: must reuse the caller's transactional connection so the
    // stock change is rolled back together with the order if anything fails.
    public boolean decrementStock(Connection connection, UUID productId, int quantity) {

        String sql = """
                UPDATE products
                SET stock_quantity = stock_quantity - ?
                WHERE product_id = ?
                AND stock_quantity >= ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setInt(1, quantity);
            ps.setObject(2, productId);
            ps.setInt(3, quantity);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at decrementStock ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at decrementStock ", e);
        }

        return false;
    }

    // Enable or disable product
    public boolean updateStatus(UUID productId, boolean isActive) {

        String sql = """
                UPDATE products
                SET is_active = ?
                WHERE product_id = ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setBoolean(1, isActive);
            ps.setObject(2, productId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOG.error("sql exception at updateStatus ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateStatus ", e);
        }

        return false;
    }

    // Count total products
    public int count() {

        String sql = "SELECT COUNT(*) FROM products";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at count ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at count ", e);
        }

        return 0;
    }

    // Count products by category
    public int countByCategory(String category) {

        String sql = """
                SELECT COUNT(*) FROM products
                WHERE category ILIKE ?
                """;

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, category);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LOG.error("sql exception at countByCategory ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at countByCategory ", e);
        }

        return 0;
    }

    // ------------------------------------------------------------------
    // Search products with dynamic filters and relevance ranking.
    //
    // Filters (all optional):
    //   q          - keyword matched against name/description/category/ingredients
    //                using Postgres full-text search (websearch_to_tsquery),
    //                with a name-prefix fallback so short typed queries
    //                like "gh" still surface "Ghee Mysore Pak".
    //   categories - zero-or-more exact category matches (OR-combined)
    //   minPrice   - lower bound (inclusive)
    //   maxPrice   - upper bound (inclusive)
    //   inStock    - when true, only stock_quantity > 0
    //
    // Sort:
    //   relevance  (default when q is set) - ts_rank_cd over the combined
    //                                        tsvector, name-prefix boost, then newest
    //   newest     (default when no q)     - created_at DESC
    //   price_asc
    //   price_desc
    //
    // Only active products are returned. Pagination via limit/offset.
    //
    // Performance note: for large catalogues add a GIN index on the same
    // expression we query, e.g.
    //   CREATE INDEX idx_products_fts ON products USING GIN (
    //     to_tsvector('english',
    //       coalesce(name,'') || ' ' || coalesce(description,'') || ' ' ||
    //       coalesce(category,'') || ' ' || coalesce(ingredients,'')));
    // ------------------------------------------------------------------
    public List<Product> search(
            String q,
            List<String> categories,
            Double minPrice,
            Double maxPrice,
            boolean inStock,
            String sort,
            int limit,
            int offset
    ) {

        StringBuilder sql = new StringBuilder(
                "SELECT * FROM products WHERE is_active = true"
        );

        List<Object> params = new ArrayList<>();

        // The same tsvector expression is referenced from the WHERE clause
        // and the ORDER BY (for ts_rank). Keep it in one place so a GIN
        // expression index can match exactly.
        final String TSV =
                "to_tsvector('english', " +
                "  coalesce(name,'')        || ' ' || " +
                "  coalesce(description,'') || ' ' || " +
                "  coalesce(category,'')    || ' ' || " +
                "  coalesce(ingredients,''))";

        String tsQuery = "";          // raw user text fed to websearch_to_tsquery
        String prefixLike = "\u0000"; // 'gh%' style fallback on name
        String substrLike = "\u0000"; // '%ghee%' style fallback on ingredients
        boolean hasQ = false;
        if (q != null && !q.trim().isEmpty()) {
            hasQ = true;
            tsQuery = q.trim();
            prefixLike = tsQuery + "%";
            substrLike = "%" + tsQuery + "%";
            // websearch_to_tsquery understands quoted phrases, OR, and -negation
            // out of the box, so "ghee -sugar" or "\"mysore pak\"" just work.
            // The two ILIKE branches keep short prefix matches (FTS only matches
            // whole tokens) and substring matches inside the comma-separated
            // ingredients string usable.
            sql.append(" AND (")
               .append(TSV).append(" @@ websearch_to_tsquery('english', ?) ")
               .append(" OR name ILIKE ? ")
               .append(" OR ingredients ILIKE ? ")
               .append(")");
            params.add(tsQuery);
            params.add(prefixLike);
            params.add(substrLike);
        }
        // Categories: zero -> no filter; one or more -> OR-combined ILIKE.
        // We use ILIKE per value so the match stays case-insensitive,
        // matching the previous single-category behaviour.
        if (categories != null && !categories.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String c : categories) {
                if (c != null && !c.trim().isEmpty()) cleaned.add(c.trim());
            }
            if (!cleaned.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < cleaned.size(); i++) {
                    if (i > 0) sql.append(" OR ");
                    sql.append("category ILIKE ?");
                    params.add(cleaned.get(i));
                }
                sql.append(")");
            }
        }

        if (minPrice != null) {
            sql.append(" AND price >= ?");
            params.add(minPrice);
        }

        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            params.add(maxPrice);
        }

        if (inStock) {
            sql.append(" AND stock_quantity > 0");
        }

        // ORDER BY
        String s = sort == null ? "" : sort.trim().toLowerCase();
        if ("price_asc".equals(s)) {
            sql.append(" ORDER BY price ASC, created_at DESC");
        } else if ("price_desc".equals(s)) {
            sql.append(" ORDER BY price DESC, created_at DESC");
        } else if ("newest".equals(s) || (!hasQ && s.isEmpty())) {
            sql.append(" ORDER BY created_at DESC");
        } else {
            // relevance: ts_rank_cd over the combined tsvector, then a small
            // boost for products whose NAME starts with the typed text (so
            // "ghee" still pushes "Ghee Mysore Pak" above a recipe that
            // happens to list ghee as ingredient #4), then newest.
            sql.append(
                " ORDER BY " +
                "  ts_rank_cd(" + TSV + ", websearch_to_tsquery('english', ?)) DESC, " +
                "  (CASE WHEN name ILIKE ? THEN 1 ELSE 0 END) DESC, " +
                "  created_at DESC"
            );
            // When q is empty (e.g. browsing with sort=relevance) both
            // expressions evaluate to 0/false for every row and the order
            // effectively collapses to created_at DESC.
            params.add(tsQuery);
            params.add(prefixLike);
        }

        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.error("sql exception at search ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at search ", e);
        }

        return products;
    }

    // ------------------------------------------------------------------
    // Recommendation: other active products in the same category, excluding
    // the source product. Used for the "you may also like" rail.
    // ------------------------------------------------------------------
    public List<Product> findRelatedByCategory(UUID productId, String category, int limit) {

        String sql = """
                SELECT * FROM products
                WHERE category ILIKE ?
                  AND product_id <> ?
                  AND is_active = true
                  AND stock_quantity > 0
                ORDER BY created_at DESC
                LIMIT ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, category);
            ps.setObject(2, productId);
            ps.setInt(3, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findRelatedByCategory ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findRelatedByCategory ", e);
        }

        return products;
    }

    // ------------------------------------------------------------------
    // Distinct active category list, used to populate the storefront
    // filter sidebar dynamically (instead of a hard-coded array on the
    // client). Trims blanks and ignores soft-deleted products.
    // ------------------------------------------------------------------
    public List<String> findDistinctCategories() {

        String sql = """
                SELECT DISTINCT category
                FROM products
                WHERE is_active = true
                  AND category IS NOT NULL
                  AND TRIM(category) <> ''
                ORDER BY category
                """;

        List<String> categories = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findDistinctCategories ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findDistinctCategories ", e);
        }
        return categories;
    }

    // ------------------------------------------------------------------
    // Recommendation: "customers who bought this also bought".
    //
    // Strategy: find every order that contains the given productId, then
    // collect all OTHER products from those orders, count their frequency,
    // and return the top N most-frequently-co-purchased products.
    //
    // We only consider currently-active in-stock products so the rail can
    // be rendered as click-to-buy.
    // ------------------------------------------------------------------
    public List<Product> findAlsoBought(UUID productId, int limit) {

        String sql = """
                SELECT p.*, COUNT(*) AS co_count
                FROM order_items oi_self
                JOIN order_items oi_other
                  ON oi_self.order_id = oi_other.order_id
                 AND oi_self.product_id <> oi_other.product_id
                JOIN products p
                  ON p.product_id = oi_other.product_id
                WHERE oi_self.product_id = ?
                  AND p.is_active = true
                  AND p.stock_quantity > 0
                GROUP BY p.product_id
                ORDER BY co_count DESC, p.created_at DESC
                LIMIT ?
                """;

        List<Product> products = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setObject(1, productId);
            ps.setInt(2, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                products.add(mapRow(rs));
            }

        } catch (SQLException e) {
            LOG.error("sql exception at findAlsoBought ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at findAlsoBought ", e);
        }

        return products;
    }

    // Map resultset to product
    private Product mapRow(ResultSet rs) throws SQLException {

        Product product = new Product();

        product.setId(
                rs.getObject("product_id", java.util.UUID.class)
        );

        product.setName(rs.getString("name"));

        product.setDescription(rs.getString("description"));

        product.setCategory(rs.getString("category"));

        product.setIngredients(rs.getString("ingredients"));

        // Tamil fields are nullable + were added in a later migration.
        // Read defensively so legacy DBs without the columns do not
        // break the row mapping. Returns null if the column is missing.
        product.setNameTamil(getStringIfPresent(rs, "name_tamil"));
        product.setDescriptionTamil(getStringIfPresent(rs, "description_tamil"));
        product.setIngredientsTamil(getStringIfPresent(rs, "ingredients_tamil"));

        product.setPrice(rs.getDouble("price"));

        product.setStockQuantity(rs.getInt("stock_quantity"));

        product.setActive(rs.getBoolean("is_active"));

        product.setCreatedAt(rs.getTimestamp("created_at"));

        product.setUpdatedAt(rs.getTimestamp("updated_at"));

        return product;
    }

    // Returns the column value, or null if the column is not present
    // in the current ResultSet. Used for fields that may not exist in
    // an older schema.
    private static String getStringIfPresent(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException ignored) {
            return null;
        }
    }

    // Normalises empty strings to NULL before persisting so the DB
    // stores a clean absence-of-value for optional fields.
    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}