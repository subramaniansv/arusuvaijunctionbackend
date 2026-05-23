package com.ecommerce.app.module.wishlist;

import java.sql.Timestamp;
import java.util.UUID;

import com.ecommerce.app.module.product.Product;

/**
 * One row in the wishlist_items table, plus an inline snapshot of the
 * referenced product so list endpoints can return a single de-normalised
 * payload without the client doing N+1 lookups.
 *
 * The {@code product} field is populated by repository queries that
 * join wishlist_items with products + the primary image + review
 * aggregates (same shape as the catalog list view).
 */
public class WishlistItem {
    private UUID userId;
    private UUID productId;
    private Timestamp createdAt;
    private Product product;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}
