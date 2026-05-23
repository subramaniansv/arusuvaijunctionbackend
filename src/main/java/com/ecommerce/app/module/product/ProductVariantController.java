package com.ecommerce.app.module.product;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.common.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin CRUD for product variants.
 *
 *   GET    /api/product/variant?productId=<uuid>[&includeInactive=true]
 *       List variants of a product. Anonymous callers always get the
 *       active variants (storefront use). Admins may pass
 *       includeInactive=true to see soft-deleted entries too — non-admin
 *       callers cannot escalate by setting the flag.
 *
 *   POST   /api/product/variant                              (Admin)
 *       Body: { "productId": "...", "label": "250g", "price": 120,
 *               "stockQuantity": 30, "sortOrder": 0, "isActive": true }
 *
 *   PUT    /api/product/variant?variantId=<uuid>             (Admin)
 *       Body: any subset of label, price, stockQuantity, sortOrder, isActive
 *
 *   DELETE /api/product/variant?variantId=<uuid>             (Admin)
 *       Hard-deletes. Use PUT with isActive=false for soft-delete instead.
 *
 * The controller never returns variants for an invalid/missing productId
 * (read endpoints) so admin UIs can show stable empty states.
 */
public class ProductVariantController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ProductVariantController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProductVariantRepository repo = new ProductVariantRepository();

    // -------- list --------
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID productId = parseUuid(request.getParameter("productId"));
            if (productId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            // includeInactive is admin-only — silently ignore for everyone else
            boolean includeInactive = "true".equalsIgnoreCase(request.getParameter("includeInactive"))
                    && isAdmin(request);
            List<ProductVariant> variants = repo.findByProductId(productId, includeInactive);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "variants fetched", variants, 200), response);
        } catch (Exception e) {
            LOG.error("variant list failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not fetch variants", null, 500), response);
        }
    }

    // -------- create --------
    @RequiresRole("Admin")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body == null || !body.hasNonNull("productId") || !body.hasNonNull("label")) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId and label are required", null, 400), response);
                return;
            }
            UUID productId = parseUuid(body.get("productId").asText());
            if (productId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "invalid productId", null, 400), response);
                return;
            }
            String label = body.get("label").asText().trim();
            if (label.isEmpty()) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "label cannot be blank", null, 400), response);
                return;
            }
            ProductVariant v = new ProductVariant();
            v.setProductId(productId);
            v.setLabel(label);
            v.setPrice(body.hasNonNull("price") ? body.get("price").asDouble(0) : 0);
            v.setStockQuantity(body.hasNonNull("stockQuantity") ? body.get("stockQuantity").asInt(0) : 0);
            v.setSortOrder(body.hasNonNull("sortOrder") ? body.get("sortOrder").asInt(0) : 0);
            v.setActive(body.hasNonNull("isActive") ? body.get("isActive").asBoolean(true) : true);

            ProductVariant saved = repo.create(v);
            if (saved == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "could not create variant", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "variant created", saved, 200), response);
        } catch (Exception e) {
            LOG.error("variant create failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not create variant", null, 500), response);
        }
    }

    // -------- update --------
    @RequiresRole("Admin")
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID variantId = parseUuid(request.getParameter("variantId"));
            if (variantId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "variantId is required", null, 400), response);
                return;
            }
            // Read the existing row first so we can apply a partial patch
            // instead of forcing the admin UI to resend every field.
            ProductVariant current = repo.findById(variantId);
            if (current == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "variant not found", null, 404), response);
                return;
            }
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body == null) body = MAPPER.createObjectNode();

            if (body.hasNonNull("label")) {
                String label = body.get("label").asText().trim();
                if (label.isEmpty()) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "label cannot be blank", null, 400), response);
                    return;
                }
                current.setLabel(label);
            }
            if (body.hasNonNull("price")) {
                current.setPrice(body.get("price").asDouble());
            }
            if (body.hasNonNull("stockQuantity")) {
                current.setStockQuantity(body.get("stockQuantity").asInt());
            }
            if (body.hasNonNull("sortOrder")) {
                current.setSortOrder(body.get("sortOrder").asInt());
            }
            if (body.hasNonNull("isActive")) {
                current.setActive(body.get("isActive").asBoolean());
            }

            ProductVariant saved = repo.update(current);
            if (saved == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "could not update variant", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "variant updated", saved, 200), response);
        } catch (Exception e) {
            LOG.error("variant update failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not update variant", null, 500), response);
        }
    }

    // -------- delete --------
    @RequiresRole("Admin")
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID variantId = parseUuid(request.getParameter("variantId"));
            if (variantId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "variantId is required", null, 400), response);
                return;
            }
            boolean ok = repo.delete(variantId);
            if (!ok) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "variant not found", null, 404), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "variant deleted", null, 200), response);
        } catch (Exception e) {
            LOG.error("variant delete failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not delete variant", null, 500), response);
        }
    }

    // -------- helpers --------

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Best-effort admin check for read-side conveniences (e.g. allowing
     * inactive variants in the list response). Writes do not rely on
     * this — they are protected by {@code @RequiresRole("Admin")} which
     * the authorization filter enforces. We re-parse the bearer token
     * here because the filter does not stash the user on the request.
     */
    private static boolean isAdmin(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) return false;
            String token = header.substring("Bearer ".length()).trim();
            com.ecommerce.app.module.iam.security.AuthUser user =
                    com.ecommerce.app.module.iam.security.AuthUser.getAuthUser(token);
            return user != null && user.hasRole("Admin");
        } catch (Exception ignored) {
            return false;
        }
    }
}
