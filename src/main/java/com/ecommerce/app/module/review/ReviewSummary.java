package com.ecommerce.app.module.review;

public class ReviewSummary {
    private double averageRating;
    private int reviewCount;

    public ReviewSummary() {}
    public ReviewSummary(double averageRating, int reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
    }

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
}
