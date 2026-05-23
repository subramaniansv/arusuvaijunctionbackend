package com.ecommerce.app.module.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Product reviews.
 *
 *   GET    /api/review?productId=<uuid>           -> list reviews + summary (public)
 *   POST   /api/review                            -> create/update own review (auth)
 *                                                    body: { productId, rating (1-5), comment }
 *   DELETE /api/review?reviewId=<uuid>            -> delete own review (auth)
 */
@WebServlet("/api/review")
public class ReviewController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewController.class);


    private final ReviewService service = new ReviewService();
    private static final ObjectMapper mapper = new ObjectMapper();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Featured testimonials feed for the home page.
        // GET /api/review?featured=true&limit=12   -> public, no productId required.
        boolean featured = "true".equalsIgnoreCase(request.getParameter("featured"));
        if (featured) {
            int limit = parseIntOrDefault(request.getParameter("limit"), 12);
            if (limit <= 0 || limit > 50) limit = 12;
            List<Review> featuredReviews = service.getFeatured(limit);
            Map<String, Object> payload = new HashMap<>();
            payload.put("reviews", featuredReviews);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "featured reviews fetched", payload, 200), response);
            return;
        }

        String productIdParam = request.getParameter("productId");
        if (productIdParam == null || productIdParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "productId is required", null, 400), response);
            return;
        }
        UUID productId;
        try {
            productId = UUID.fromString(productIdParam);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid productId", null, 400), response);
            return;
        }
        int limit = parseIntOrDefault(request.getParameter("limit"), 20);
        int offset = parseIntOrDefault(request.getParameter("offset"), 0);
        if (limit <= 0 || limit > 100) limit = 20;
        if (offset < 0) offset = 0;

        List<Review> reviews = service.getForProduct(productId, limit, offset);
        ReviewSummary summary = service.getSummary(productId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("reviews", reviews);
        SendResponseUtil.sendResponse(new ApiResponse(true, "reviews fetched", payload, 200), response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
            return;
        }
        try {
            JsonNode body = mapper.readTree(request.getInputStream());
            if (!body.hasNonNull("productId") || !body.hasNonNull("rating")) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId and rating are required", null, 400), response);
                return;
            }
            UUID productId;
            try {
                productId = UUID.fromString(body.get("productId").asText());
            } catch (IllegalArgumentException e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid productId", null, 400), response);
                return;
            }
            int rating = body.get("rating").asInt();
            String comment = body.hasNonNull("comment") ? body.get("comment").asText() : null;

            Review saved = service.submit(user.getUserId(), productId, rating, comment);
            if (saved == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not save review", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(new ApiResponse(true, "review saved", saved, 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at review controller doPost ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not save review", null, 500), response);
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
            return;
        }
        String reviewIdParam = request.getParameter("reviewId");
        if (reviewIdParam == null || reviewIdParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "reviewId is required", null, 400), response);
            return;
        }
        UUID reviewId;
        try {
            reviewId = UUID.fromString(reviewIdParam);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid reviewId", null, 400), response);
            return;
        }
        boolean deleted = service.deleteOwn(reviewId, user.getUserId());
        if (!deleted) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "review not found or not yours", null, 404), response);
            return;
        }
        SendResponseUtil.sendResponse(new ApiResponse(true, "review deleted", null, 200), response);
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return fallback; }
    }
}
