package com.ecommerce.app.module.banner;

import com.ecommerce.app.common.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.product.image.StorageFactory;
import com.ecommerce.app.module.product.image.StorageService;
import com.ecommerce.app.util.SendResponseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Festival Banner System — "spotlight" model.
 *
 * Business model: Arusuvai Junction is a FOOD business. At any moment exactly
 * ONE festival is live on the client. Admin flips that spotlight; the active
 * festival's background images then appear on the public /festival page, where
 * a visitor picks an image, writes a message and shares a link.
 *
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │  PUBLIC  (no JWT — whitelisted in AuthorizationFilter)               │
 * │                                                                       │
 * │  GET  /api/banner/festival                                            │
 * │       → the ONE active festival + its active images (client page)     │
 * │                                                                       │
 * │  GET  /api/banner/festivals                                           │
 * │       → list of active festivals (kept for compatibility)             │
 * │                                                                       │
 * │  GET  /api/banner/festivals/<slug>/images                             │
 * │       → active images for a festival                                   │
 * │                                                                       │
 * │  GET  /api/banner/share/<slug>                                        │
 * │       → message + chosen bg image (the shared page payload)           │
 * │                                                                       │
 * │  POST /api/banner/admin/messages                                      │
 * │       → anyone on /festival creates a greeting → shareable slug        │
 * │                                                                       │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │  ADMIN  (Bearer JWT + "Admin" role)                                   │
 * │                                                                       │
 * │  GET    /api/banner/admin/festivals          all festivals (incl. ⛔) │
 * │  POST   /api/banner/admin/festivals          create festival (⛔ off)  │
 * │  PUT    /api/banner/admin/festivals          spotlight on / turn off  │
 * │                                                                       │
 * │  GET    /api/banner/admin/images             list images by festival  │
 * │  POST   /api/banner/admin/images             upload image (multipart) │
 * │  DELETE /api/banner/admin/images             disable image            │
 * │                                                                       │
 * │  GET    /api/banner/admin/messages           list all messages        │
 * │  PUT    /api/banner/admin/messages           toggle active            │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * Images live in the same R2 / MinIO bucket used by product images
 * (StorageFactory.get()), under the "festival/" key prefix.
 */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,          // 1 MB  — buffer in memory
        maxFileSize       = 10L * 1024L * 1024L,  // 10 MB per file
        maxRequestSize    = 15L * 1024L * 1024L   // 15 MB whole request
)
public class BannerController extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(BannerController.class);

    private final FestivalCategoryRepository categoryRepo = new FestivalCategoryRepository();
    private final FestivalImageRepository    imageRepo    = new FestivalImageRepository();
    private final FestivalMessageRepository  messageRepo  = new FestivalMessageRepository();

    private final StorageService storage = StorageFactory.get();

    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =========================================================================
    // GET
    // =========================================================================
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = normPath(req.getPathInfo());

        try {
            // ── PUBLIC ───────────────────────────────────────────────────────

            // GET /api/banner/festival
            // The spotlight payload for the client /festival page:
            //   { festival: {...} | null, images: [...] }
            // null festival  → admin has nothing live; client shows empty state.
            if ("/festival".equals(path)) {
                Optional<FestivalCategory> active = categoryRepo.findActive();
                Map<String, Object> payload = new HashMap<>();
                if (active.isEmpty()) {
                    payload.put("festival", null);
                    payload.put("images", List.of());
                } else {
                    FestivalCategory fc = active.get();
                    payload.put("festival", fc);
                    payload.put("images", imageRepo.findActiveByFestival(fc.getFestivalId()));
                }
                ok(res, payload);
                return;
            }

            // GET /api/banner/festivals  — active festivals list (compat)
            if ("/festivals".equals(path)) {
                ok(res, categoryRepo.findAllActive());
                return;
            }

            // GET /api/banner/festivals/all/images  — all active images (home hero)
            if ("/festivals/all/images".equals(path)) {
                ok(res, imageRepo.findAllActive());
                return;
            }

            // GET /api/banner/festivals/<slug>/images
            if (path.startsWith("/festivals/") && path.endsWith("/images")) {
                String inner = path.substring("/festivals/".length(),
                        path.length() - "/images".length());
                if (inner.isBlank() || inner.contains("/")) {
                    notFound(res, "Invalid festival slug"); return;
                }
                UUID festId = festivalIdBySlug(inner);
                if (festId == null) { notFound(res, "Festival not found: " + inner); return; }
                ok(res, imageRepo.findActiveByFestival(festId));
                return;
            }

            // GET /api/banner/share/<slug>  — shared page payload
            if (path.startsWith("/share/")) {
                String shareSlug = path.substring("/share/".length());
                if (shareSlug.isBlank()) { badRequest(res, "share slug is required"); return; }
                Optional<FestivalMessage> msgOpt = messageRepo.findBySlug(shareSlug);
                if (msgOpt.isEmpty()) { notFound(res, "Message not found or inactive"); return; }

                FestivalMessage msg = msgOpt.get();
                // Prefer the exact image the creator chose; fall back to random.
                FestivalImage bg = null;
                if (msg.getImageId() != null) {
                    bg = imageRepo.findById(msg.getImageId()).orElse(null);
                }
                if (bg == null) {
                    bg = imageRepo.findRandomActive(msg.getFestivalId()).orElse(null);
                }
                msg.setBackgroundImage(bg);
                ok(res, msg);
                return;
            }

            // ── ADMIN (inline guard — doGet serves both public + admin) ──────

            if ("/admin/festivals".equals(path)) {
                assertAdmin(res);
                ok(res, categoryRepo.findAll());
                return;
            }

            if ("/admin/images".equals(path)) {
                assertAdmin(res);
                UUID festId = parseUuid(req.getParameter("festivalId"));
                if (festId == null) { badRequest(res, "festivalId is required"); return; }
                ok(res, imageRepo.findAllByFestival(festId));
                return;
            }

            if ("/admin/messages".equals(path)) {
                assertAdmin(res);
                ok(res, messageRepo.findAll());
                return;
            }

            notFound(res, "Unknown path: " + path);

        } catch (AccessDenied ad) {
            // 403 already written by assertAdmin()
        } catch (Exception e) {
            LOG.error("BannerController GET error path={}", path, e);
            error(res, e.getMessage());
        }
    }

    // =========================================================================
    // POST
    //   PUBLIC : /admin/messages  (whitelisted; no @RequiresRole on method)
    //   ADMIN  : /admin/festivals, /admin/images  (inline assertAdmin())
    // =========================================================================
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = normPath(req.getPathInfo());

        try {
            // POST /api/banner/admin/messages — PUBLIC: no login required.
            if ("/admin/messages".equals(path)) {
                FestivalMessage msg = om.readValue(req.getReader(), FestivalMessage.class);
                if (blank(msg.getMessageText())) {
                    badRequest(res, "messageText is required"); return;
                }
                if (msg.getFestivalId() == null) {
                    badRequest(res, "festivalId is required"); return;
                }
                UUID callerId = null;
                try {
                    AuthUser caller = AuthContext.get();
                    if (caller != null) callerId = caller.getUserId();
                } catch (Exception ignored) {}
                created(res, messageRepo.create(msg, callerId));
                return;
            }

            // ── ADMIN paths below ─────────────────────────────────────────────

            // POST /api/banner/admin/festivals — create festival (starts disabled)
            if ("/admin/festivals".equals(path)) {
                assertAdmin(res);
                FestivalCategory fc = om.readValue(req.getReader(), FestivalCategory.class);
                if (blank(fc.getName()) || blank(fc.getSlug())) {
                    badRequest(res, "name and slug are required"); return;
                }
                created(res, categoryRepo.create(fc));
                return;
            }

            // POST /api/banner/admin/images — upload image (multipart)
            if ("/admin/images".equals(path)) {
                assertAdmin(res);
                UUID adminId = AuthContext.get().getUserId();

                UUID festId = parseUuid(req.getParameter("festivalId"));
                if (festId == null) { badRequest(res, "festivalId is required"); return; }

                String label = req.getParameter("label");
                int    order = parseInt(req.getParameter("displayOrder"), 0);

                Part filePart = findFilePart(req);
                if (filePart == null) {
                    badRequest(res, "image file is required (field name: 'image' or 'file')"); return;
                }

                String fileName  = sanitizeFileName(filePart.getSubmittedFileName());
                String objectKey;
                try (InputStream in = filePart.getInputStream()) {
                    objectKey = storage.upload(
                            "festival/" + fileName,
                            in,
                            filePart.getSize(),
                            filePart.getContentType()
                    );
                }
                String imageUrl = storage.getFileUrl(objectKey);

                FestivalImage img = new FestivalImage();
                img.setFestivalId   (festId);
                img.setImageUrl     (imageUrl);
                img.setObjectKey    (objectKey);
                img.setLabel        (label);
                img.setDisplayOrder (order);

                created(res, imageRepo.create(img, adminId));
                return;
            }

            notFound(res, "Unknown path: " + path);

        } catch (AccessDenied ad) {
            // 403 already written
        } catch (Exception e) {
            LOG.error("BannerController POST error path={}", path, e);
            error(res, e.getMessage());
        }
    }

    // =========================================================================
    // PUT — all admin
    // =========================================================================
    @Override
    @RequiresRole("Admin")
    protected void doPut(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = normPath(req.getPathInfo());

        try {
            // PUT /api/banner/admin/festivals?festivalId=<uuid>&active=true|false
            // active=true  → spotlight: enable this one, disable all others.
            // active=false → simply turn this one off.
            if ("/admin/festivals".equals(path)) {
                UUID festId = parseUuid(req.getParameter("festivalId"));
                if (festId == null) { badRequest(res, "festivalId is required"); return; }
                boolean active = Boolean.parseBoolean(req.getParameter("active"));
                boolean ok = categoryRepo.setActive(festId, active);
                respond(res, ok,
                        ok ? (active ? "Festival is now live" : "Festival turned off")
                           : "Festival not found",
                        ok ? 200 : 404);
                return;
            }

            // PUT /api/banner/admin/messages?messageId=<uuid>&active=true|false
            if ("/admin/messages".equals(path)) {
                UUID msgId = parseUuid(req.getParameter("messageId"));
                if (msgId == null) { badRequest(res, "messageId is required"); return; }
                boolean active = Boolean.parseBoolean(req.getParameter("active"));
                boolean ok = messageRepo.setActive(msgId, active);
                respond(res, ok, ok ? "Message updated" : "Message not found", ok ? 200 : 404);
                return;
            }

            notFound(res, "Unknown path: " + path);

        } catch (Exception e) {
            LOG.error("BannerController PUT error path={}", path, e);
            error(res, e.getMessage());
        }
    }

    // =========================================================================
    // DELETE — all admin
    // =========================================================================
    @Override
    @RequiresRole("Admin")
    protected void doDelete(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String path = normPath(req.getPathInfo());

        try {
            if ("/admin/images".equals(path)) {
                UUID imageId = parseUuid(req.getParameter("imageId"));
                if (imageId == null) { badRequest(res, "imageId is required"); return; }

                Optional<FestivalImage> imgOpt = imageRepo.findById(imageId);
                if (imgOpt.isEmpty()) { notFound(res, "Image not found"); return; }

                String key = imgOpt.get().getObjectKey();
                if (!blank(key)) {
                    try {
                        storage.delete(key);
                        LOG.info("Purged festival image from storage: {}", key);
                    } catch (Exception ex) {
                        LOG.warn("Storage delete failed for key={}, continuing", key, ex);
                    }
                }

                boolean ok = imageRepo.disable(imageId);
                respond(res, ok, ok ? "Image removed" : "Image not found", ok ? 200 : 404);
                return;
            }

            notFound(res, "Unknown path: " + path);

        } catch (Exception e) {
            LOG.error("BannerController DELETE error path={}", path, e);
            error(res, e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void assertAdmin(HttpServletResponse res) throws IOException, AccessDenied {
        AuthUser user = AuthContext.get();
        if (user == null || !user.hasRole("Admin")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Access denied: Admin role required", null, 403), res);
            throw new AccessDenied();
        }
    }

    private UUID festivalIdBySlug(String slug) throws Exception {
        for (FestivalCategory fc : categoryRepo.findAllActive()) {
            if (fc.getSlug().equals(slug)) return fc.getFestivalId();
        }
        // Also allow inactive festivals to be resolved by slug (admin preview)
        for (FestivalCategory fc : categoryRepo.findAll()) {
            if (fc.getSlug().equals(slug)) return fc.getFestivalId();
        }
        return null;
    }

    // ── Response shorthands ──────────────────────────────────────────────
    private void ok(HttpServletResponse res, Object data) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(true, "OK", data, 200), res);
    }
    private void created(HttpServletResponse res, Object data) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(true, "Created", data, 201), res);
    }
    private void badRequest(HttpServletResponse res, String msg) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(false, msg, null, 400), res);
    }
    private void notFound(HttpServletResponse res, String msg) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(false, msg, null, 404), res);
    }
    private void error(HttpServletResponse res, String msg) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(false, "Internal server error: " + msg, null, 500), res);
    }
    private void respond(HttpServletResponse res, boolean ok, String msg, int status) throws IOException {
        SendResponseUtil.sendResponse(new ApiResponse(ok, msg, null, status), res);
    }

    // ── Misc utils ───────────────────────────────────────────────────────

    private static String normPath(String info) {
        if (info == null || info.isEmpty()) return "";
        return info.endsWith("/") && info.length() > 1
                ? info.substring(0, info.length() - 1) : info;
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s.trim()); } catch (Exception e) { return null; }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Part findFilePart(HttpServletRequest req) throws IOException, ServletException {
        for (Part p : req.getParts()) {
            if (("image".equals(p.getName()) || "file".equals(p.getName())) && p.getSize() > 0)
                return p;
        }
        return null;
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        String name = slash >= 0 ? raw.substring(slash + 1) : raw;
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_")
                             .replaceAll("_+", "_")
                             .replaceAll("^_+|_+$", "");
        return cleaned.isEmpty() ? "file" : cleaned;
    }

    private static final class AccessDenied extends Exception {
        AccessDenied() { super(null, null, true, false); }
    }
}
