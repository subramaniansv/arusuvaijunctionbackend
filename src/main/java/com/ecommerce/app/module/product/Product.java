package com.ecommerce.app.module.product;

import java.sql.Timestamp;
import java.util.*;

import com.ecommerce.app.module.review.Review;
import com.fasterxml.jackson.annotation.JsonAlias;

public class Product{
   @JsonAlias("productId")
   private UUID id;
    private String name;
    private String description;
    private String category;
    private String ingredients;
    private double price;
    private int stockQuantity;
    private boolean isActive;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<ProductImage> images;
    // URL of the is_primary=true row in product_images. Hydrated everywhere
    // we return product info (list, detail, search, recommendations, admin)
    // so the frontend can render a thumbnail without a second round-trip
    // or having to walk the full `images` list.
    private String primaryImageUrl;
    // Review fields - populated only when fetching a single product
    // (averageRating + reviewCount on the list view would mean an N+1 query).
    private Double averageRating;
    private Integer reviewCount;
    private List<Review> reviews;
    // Purchasable variants (e.g. "250g", "500g", "5 pcs"). Hydrated only
    // by the detail endpoint - the list view shows the base price/stock
    // and lets the user pick a size on the detail page. Empty list when
    // the product ships in a single size; the frontend then falls back
    // to product.price + product.stockQuantity.
    private List<ProductVariant> variants;
    public UUID getId() {
        return id;
    }
    public void setId(UUID id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public String getIngredients() {
        return ingredients;
    }
    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public int getStockQuantity() {
        return stockQuantity;
    }
    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
    public boolean isActive() {
        return isActive;
    }
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    public List<ProductImage> getImages() {
        return images;
    }
    public void setImages(List<ProductImage> images) {
        this.images = images;
    }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public List<Review> getReviews() { return reviews; }
    public void setReviews(List<Review> reviews) { this.reviews = reviews; }

    public List<ProductVariant> getVariants() { return variants; }
    public void setVariants(List<ProductVariant> variants) { this.variants = variants; }

}