package com.ecommerce.app.module.product;

import java.util.*;

/**
 * Service-only module (no controller, per project scope) that produces the
 * two recommendation rails used on the product detail page:
 *
 *   1. Same-category    - other in-stock products in the same category.
 *   2. Also-bought      - frequently co-purchased products derived from
 *                         historical order_items rows.
 *
 * Results always hydrate product images so the caller can render a card
 * without a second round-trip.
 */
public class RecommendationService {

    private final ProductRepository productRepository = new ProductRepository();
    private final ProductImageRepository productImageRepository = new ProductImageRepository();

    /** Default rail size when caller doesn't specify. */
    private static final int DEFAULT_LIMIT = 8;

    /**
     * Products in the same category as the given product, excluding the
     * product itself. Returns empty list when source product is missing,
     * inactive, or has no category.
     */
    public List<Product> sameCategory(UUID productId, int limit) {
        if (productId == null) {
            return Collections.emptyList();
        }
        Product source = productRepository.findById(productId);
        if (source == null || source.getCategory() == null) {
            return Collections.emptyList();
        }
        List<Product> products = productRepository.findRelatedByCategory(
                productId, source.getCategory(), limit > 0 ? limit : DEFAULT_LIMIT);
        return attachImages(products);
    }

    /**
     * "Customers who bought this also bought" — co-purchase frequency from
     * the order_items join. Returns empty list when there's no purchase
     * history yet for this product (a brand-new SKU).
     */
    public List<Product> alsoBought(UUID productId, int limit) {
        if (productId == null) {
            return Collections.emptyList();
        }
        List<Product> products = productRepository.findAlsoBought(
                productId, limit > 0 ? limit : DEFAULT_LIMIT);
        return attachImages(products);
    }

    /**
     * Convenience: returns both rails in one call so the controller doesn't
     * need to know the two pieces exist.
     */
    public Map<String, List<Product>> recommendationsFor(UUID productId, int limit) {
        Map<String, List<Product>> bundle = new LinkedHashMap<>();
        bundle.put("sameCategory", sameCategory(productId, limit));
        bundle.put("alsoBought", alsoBought(productId, limit));
        return bundle;
    }

    private List<Product> attachImages(List<Product> products) {
        // Batch-fetch primary image urls so each rail does one extra query
        // instead of one query per product. Recommendation rails live on
        // the hot product detail page so the N+1 here was particularly
        // visible.
        List<UUID> ids = new ArrayList<>(products.size());
        for (Product p : products) {
            ids.add(p.getId());
        }
        Map<UUID, String> primaryUrls = productImageRepository.findPrimaryUrlsByProductIds(ids);
        for (Product p : products) {
            p.setPrimaryImageUrl(primaryUrls.get(p.getId()));
        }
        return products;
    }
}
