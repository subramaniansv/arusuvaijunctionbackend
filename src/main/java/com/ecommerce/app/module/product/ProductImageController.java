package com.ecommerce.app.module.product;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.common.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

/**
 * Admin endpoints for managing the images attached to an existing
 * product. The create-product flow already accepts images as part of
 * {@code POST /api/product}; this controller covers adds + deletes
 * after the product is live.
 *
 *   POST   /api/product/image                              (Admin)
 *       multipart/form-data with two parts:
 *         productId : form field, UUID
 *         image     : the file
 *       The first image to ever land on a product is auto-promoted to
 *       primary; subsequent uploads land as secondaries.
 *
 *   PUT    /api/product/image?imageId=<uuid>&primary=true  (Admin)
 *       Promote an existing image to primary. We require imageId on the
 *       query string (not the body) so this is safe to call with no body.
 *
 *   DELETE /api/product/image?imageId=<uuid>               (Admin)
 *       Drops the R2 object and the product_images row. If the deleted
 *       row was primary the next remaining image is promoted so list
 *       views keep showing a thumbnail.
 */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 10L * 1024L * 1024L,
        maxRequestSize = 20L * 1024L * 1024L
)
public class ProductImageController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ProductImageController.class);

    private final ProductService service = new ProductService();

    @RequiresRole("Admin")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID productId = parseUuid(request.getParameter("productId"));
            if (productId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "productId is required", null, 400), response);
                return;
            }
            // Accept either an "image" or "images" part name to match the
            // shape used by the create endpoint (where it's "images").
            Part imagePart = null;
            Collection<Part> parts = request.getParts();
            for (Part p : parts) {
                if (("image".equals(p.getName()) || "images".equals(p.getName()))
                        && p.getSize() > 0) {
                    imagePart = p;
                    break;
                }
            }
            if (imagePart == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "image file is required", null, 400), response);
                return;
            }
            ProductImage saved = service.addImage(productId, imagePart);
            if (saved == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "could not upload image", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "image added", saved, 200), response);
        } catch (Exception e) {
            LOG.error("image upload failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not upload image", null, 500), response);
        }
    }

    @RequiresRole("Admin")
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID imageId = parseUuid(request.getParameter("imageId"));
            UUID productId = parseUuid(request.getParameter("productId"));
            boolean makePrimary = "true".equalsIgnoreCase(request.getParameter("primary"));
            if (imageId == null || productId == null || !makePrimary) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false,
                                "imageId, productId and primary=true are required", null, 400),
                        response);
                return;
            }
            boolean ok = service.setPrimaryImage(productId, imageId);
            if (!ok) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "image not found", null, 404), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "primary image updated", null, 200), response);
        } catch (Exception e) {
            LOG.error("set primary image failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not set primary image", null, 500), response);
        }
    }

    @RequiresRole("Admin")
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            UUID imageId = parseUuid(request.getParameter("imageId"));
            if (imageId == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "imageId is required", null, 400), response);
                return;
            }
            boolean ok = service.deleteImage(imageId);
            if (!ok) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "image not found", null, 404), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "image deleted", null, 200), response);
        } catch (Exception e) {
            LOG.error("image delete failed", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not delete image", null, 500), response);
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
