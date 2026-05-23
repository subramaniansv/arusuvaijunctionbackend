package com.ecommerce.app.module.cart;

import java.util.UUID;

public class CartItem {
    private UUID cartItemId;
    private UUID cartId;
    private UUID productId;
    // Nullable - set when the product has size/pack variants. variantLabel
    // is denormalised here so the cart UI can render the size without
    // joining product_variants on every read.
    private UUID variantId;
    private String variantLabel;
    private String productName;
    private String imageUrl;
    private double price;
    private int quantity;
    private double subtotal;

    public UUID getCartItemId() {
        return cartItemId;
    }
    public void setCartItemId(UUID cartItemId) {
        this.cartItemId = cartItemId;
    }
    public UUID getCartId() {
        return cartId;
    }
    public void setCartId(UUID cartId) {
        this.cartId = cartId;
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
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public int getQuantity() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    public double getSubtotal() {
        return subtotal;
    }
    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }
}
