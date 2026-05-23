package com.ecommerce.app.module.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Contact form messages.
 *
 *   POST   /api/contact                          -> submit a message (public)
 *                                                   body: { name, email, phone?, subject?, message }
 *
 *   GET    /api/contact?limit=&offset=&status=   -> list messages (Admin only)
 *   PATCH  /api/contact?messageId=&status=       -> update message status (Admin only)
 *   DELETE /api/contact?messageId=               -> delete (Admin only)
 *
 * Admin-only methods are guarded by {@link RequiresRole} at the method level,
 * so POST can stay public while management endpoints stay locked down.
 */
@WebServlet("/api/contact")
public class ContactController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ContactController.class);

    private final ContactService service = new ContactService();
    private static final ObjectMapper mapper = new ObjectMapper();

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            JsonNode body = mapper.readTree(request.getInputStream());
            String name    = body.hasNonNull("name")    ? body.get("name").asText()    : null;
            String email   = body.hasNonNull("email")   ? body.get("email").asText()   : null;
            String phone   = body.hasNonNull("phone")   ? body.get("phone").asText()   : null;
            String subject = body.hasNonNull("subject") ? body.get("subject").asText() : null;
            String message = body.hasNonNull("message") ? body.get("message").asText() : null;

            // Attach the user id when the submitter is signed in. POST is
            // public, so this stays null for anonymous submissions.
            AuthUser user = AuthContext.get();
            UUID userId = user != null ? user.getUserId() : null;

            ContactMessage saved = service.submit(name, email, phone, subject, message, userId);
            if (saved == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "could not save message", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "message received, we will get back to you soon", saved, 200),
                    response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at contact controller doPost ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not save message", null, 500), response);
        }
    }

    @RequiresRole("Admin")
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int limit = parseIntOrDefault(request.getParameter("limit"), 20);
        int offset = parseIntOrDefault(request.getParameter("offset"), 0);
        String status = request.getParameter("status");
        if (status != null) status = status.trim().toUpperCase();
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "messages fetched", service.list(limit, offset, status), 200),
                response);
    }

    @RequiresRole("Admin")
    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String idParam = request.getParameter("messageId");
        String status  = request.getParameter("status");
        if (idParam == null || idParam.isBlank() || status == null || status.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "messageId and status are required", null, 400), response);
            return;
        }
        UUID id;
        try { id = UUID.fromString(idParam); }
        catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid messageId", null, 400), response);
            return;
        }
        try {
            boolean ok = service.markStatus(id, status);
            if (!ok) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "message not found", null, 404), response);
                return;
            }
            SendResponseUtil.sendResponse(new ApiResponse(true, "status updated", null, 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        }
    }

    @RequiresRole("Admin")
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String idParam = request.getParameter("messageId");
        if (idParam == null || idParam.isBlank()) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "messageId is required", null, 400), response);
            return;
        }
        UUID id;
        try { id = UUID.fromString(idParam); }
        catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid messageId", null, 400), response);
            return;
        }
        boolean ok = service.delete(id);
        if (!ok) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "message not found", null, 404), response);
            return;
        }
        SendResponseUtil.sendResponse(new ApiResponse(true, "message deleted", null, 200), response);
    }

    // HttpServlet doesn't dispatch PATCH out of the box.
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
            return;
        }
        super.service(req, resp);
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return fallback; }
    }
}
