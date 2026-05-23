package com.ecommerce.app.module.wishlist;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Wishlist endpoints (all require auth via the global filter):
 *
 *   GET    /api/wishlist               -> List<WishlistItem> with embedded
 *                                          Product (incl. primaryImageUrl,
 *                                          averageRating, reviewCount).
 *   GET    /api/wishlist?ids=true      -> List<UUID> productIds only
 *                                          (lightweight for marking cards).
 *   POST   /api/wishlist {productId}   -> add (idempotent - duplicate is ok)
 *   DELETE /api/wishlist?productId=..  -> remove
 *
 * The user is taken from the JWT context, so no userId travels on the
 * wire and a logged-in user can only touch their own wishlist.
 */
@WebServlet("/api/wishlist")
public class WishlistController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(WishlistController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WishlistService service = new WishlistService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            String idsOnly = request.getParameter("ids");
            if (idsOnly != null && Boolean.parseBoolean(idsOnly)) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "wishlist ids fetched",
                                service.listProductIds(user.getUserId()), 200),
                        response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "wishlist fetched",
                            service.list(user.getUserId()), 200),
                    response);
        } catch (Exception e) {
            LOG.error("exception at wishlist controller doGet  ", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not fetch wishlist", null, 500), response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            UUID productId = readProductId(request);
            if (productId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            service.add(user.getUserId(), productId);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "added to wishlist",
                            service.list(user.getUserId()), 200),
                    response);
        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at wishlist controller doPost  ", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not add to wishlist", null, 500), response);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            String pidParam = request.getParameter("productId");
            if (pidParam == null || pidParam.isEmpty()) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            UUID productId;
            try {
                productId = UUID.fromString(pidParam);
            } catch (IllegalArgumentException ex) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "invalid productId", null, 400), response);
                return;
            }
            service.remove(user.getUserId(), productId);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "removed from wishlist",
                            service.list(user.getUserId()), 200),
                    response);
        } catch (RuntimeException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at wishlist controller doDelete  ", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not remove from wishlist", null, 500), response);
        }
    }

    private UUID readProductId(HttpServletRequest request) {
        // Accept productId from JSON body (preferred) or query string.
        try {
            JsonNode node = MAPPER.readTree(request.getInputStream());
            if (node != null && node.hasNonNull("productId")) {
                return UUID.fromString(node.get("productId").asText());
            }
        } catch (IOException | IllegalArgumentException ex) {
            // fall through to query-string parsing
        }
        String q = request.getParameter("productId");
        if (q != null && !q.isEmpty()) {
            try { return UUID.fromString(q); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
