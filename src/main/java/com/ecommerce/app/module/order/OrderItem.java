package com.ecommerce.app.module.order;

import java.util.UUID;

public class OrderItem {
    private UUID orderItemId;
    private UUID orderId;
    private UUID productId;
    // Variant snapshot - variantId may be null for products without variants.
    // variantLabel is denormalised (snapshot) so the historical order line
    // keeps its size even if the variant is later deleted or relabelled.
    private UUID variantId;
    private String variantLabel;
    private int quantity;
    private double price;
    // populated by repository SELECTs that JOIN products; used for order
    // detail responses and WhatsApp message formatting. Not persisted.
    private String productName;
    // URL of the primary product image (product_images.is_primary = true).
    // Hydrated alongside productName so the frontend can render an order
    // line with a thumbnail. Not persisted.
    private String imageUrl;
    public UUID getOrderItemId() {
        return orderItemId;
    }
    public void setOrderItemId(UUID orderItemId) {
        this.orderItemId = orderItemId;
    }
    public UUID getOrderId() {
        return orderId;
    }
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    public UUID getProductId() {
        return productId;
    }
    public void setProductId(UUID productId) {
        this.productId = productId;
    }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }
    public String getVariantLabel() { return variantLabel; }
    public void setVariantLabel(String variantLabel) { this.variantLabel = variantLabel; }
    public int getQuantity() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public String getProductName() {
        return productName;
    }
    public void setProductName(String productName) {
        this.productName = productName;
    }
    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    
}
