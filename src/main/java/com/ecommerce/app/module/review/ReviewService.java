package com.ecommerce.app.module.review;

import java.util.List;
import java.util.UUID;

import com.ecommerce.app.module.iam.repository.EmailVerificationRepository;

public class ReviewService {
    private final ReviewRepository repo = new ReviewRepository();
    private final EmailVerificationRepository emailVerificationRepository = new EmailVerificationRepository();

    public Review submit(UUID userId, UUID productId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("unauthenticated");
        }
        if (!emailVerificationRepository.isUserVerified(userId)) {
            throw new IllegalArgumentException(
                    "please verify your email before posting a review");
        }
        Review r = new Review();
        r.setUserId(userId);
        r.setProductId(productId);
        r.setRating(rating);
        r.setComment(comment);
        return repo.upsert(r);
    }

    public List<Review> getForProduct(UUID productId, int limit, int offset) {
        return repo.findByProductId(productId, limit, offset);
    }

    /** Recent high-rated reviews across all products, for the home page. */
    public List<Review> getFeatured(int limit) {
        return repo.findFeatured(limit);
    }

    public ReviewSummary getSummary(UUID productId) {
        return repo.summaryForProduct(productId);
    }

    public boolean deleteOwn(UUID reviewId, UUID userId) {
        return repo.delete(reviewId, userId);
    }

    public Review getById(UUID reviewId) {
        return repo.findById(reviewId);
    }
}
