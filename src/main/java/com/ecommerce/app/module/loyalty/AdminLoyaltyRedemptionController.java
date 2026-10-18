package com.ecommerce.app.module.loyalty;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Admin console for point value + redemption approvals. Admin-only.
 *
 *   GET  /api/admin/loyalty/redemptions/settings        -> { pointValueInr }
 *   PUT  /api/admin/loyalty/redemptions/settings  body {pointValueInr}
 *   GET  /api/admin/loyalty/redemptions?status=&limit=&offset=  -> list
 *   PATCH/api/admin/loyalty/redemptions?redemptionId=&action=approve|reject[&note=]
 */
@WebServlet(urlPatterns = { "/api/admin/loyalty/redemptions", "/api/admin/loyalty/redemptions/*" })
@RequiresRole("Admin")
public class AdminLoyaltyRedemptionController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminLoyaltyRedemptionController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LoyaltyRedemptionService service = new LoyaltyRedemptionService();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String sub = request.getPathInfo();
        if ("/settings".equals(sub)) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "loyalty settings", service.settings(), 200), response);
            return;
        }
        int limit = parseInt(request.getParameter("limit"), 20);
        int offset = parseInt(request.getParameter("offset"), 0);
        String status = request.getParameter("status");
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "redemptions fetched", service.list(limit, offset, status), 200),
                response);
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String sub = request.getPathInfo();
        if (!"/settings".equals(sub)) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unknown endpoint", null, 404), response);
            return;
        }
        AuthUser admin = AuthContext.get();
        UUID adminId = admin != null ? admin.getUserId() : null;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body == null || !body.hasNonNull("pointValueInr")) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "pointValueInr is required", null, 400), response);
                return;
            }
            BigDecimal value = body.get("pointValueInr").decimalValue();
            boolean ok = service.updatePointValue(value, adminId);
            if (!ok) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not update", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "point value updated", service.settings(), 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at admin loyalty settings update ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid payload", null, 400), response);
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser admin = AuthContext.get();
        UUID adminId = admin != null ? admin.getUserId() : null;

        String idParam = request.getParameter("redemptionId");
        String action = request.getParameter("action");
        if (idParam == null || idParam.isBlank() || action == null || action.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "redemptionId and action are required", null, 400), response);
            return;
        }
        UUID id;
        try { id = UUID.fromString(idParam); }
        catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid redemptionId", null, 400), response);
            return;
        }
        boolean approve;
        if ("approve".equalsIgnoreCase(action)) approve = true;
        else if ("reject".equalsIgnoreCase(action)) approve = false;
        else {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "action must be approve or reject", null, 400), response);
            return;
        }
        boolean ok = service.decideOffline(id, approve, adminId, readNote(request));
        if (!ok) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "redemption not found or already actioned", null, 409), response);
            return;
        }
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "redemption " + action.toLowerCase() + "d", null, 200), response);
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

    private String readNote(HttpServletRequest request) {
        String q = request.getParameter("note");
        if (q != null && !q.isBlank()) return q;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body != null && body.hasNonNull("note")) return body.get("note").asText();
        } catch (Exception ignore) { }
        return null;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return fallback; }
    }
}
