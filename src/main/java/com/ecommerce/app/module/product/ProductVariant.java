package com.ecommerce.app.module.product;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * One purchasable variant of a {@link Product} — e.g. "250g", "500g",
 * "5 pcs". Variants carry their own price and stock so the product
 * row itself stores fallback values for products that ship in a
 * single size.
 *
 * Jackson serializes this directly; field names map to JSON keys
 * the frontend already consumes (variantId, label, price, ...).
 */
public class ProductVariant {

    private UUID variantId;
    private UUID productId;
    private String label;
    private double price;
    private int stockQuantity;
    private boolean isActive = true;
    private int sortOrder;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
