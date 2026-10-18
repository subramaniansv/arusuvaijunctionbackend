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
 * Admin loyalty console. All methods are Admin-only (class-level
 * {@link RequiresRole}; the AuthorizationFilter enforces it before dispatch).
 *
 *   GET   /api/admin/loyalty?status=&limit=&offset=   -> list check-ins
 *   PATCH /api/admin/loyalty?checkinId=&action=approve|reject[&note=][&purchaseAmount=]
 *         (purchaseAmount: in-store spend used to award points on approve)
 *                                                     -> action a check-in
 */
@WebServlet("/api/admin/loyalty")
@RequiresRole("Admin")
public class AdminLoyaltyController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminLoyaltyController.class);
    private final LoyaltyService service = new LoyaltyService();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int limit = parseIntOrDefault(request.getParameter("limit"), 20);
        int offset = parseIntOrDefault(request.getParameter("offset"), 0);
        String status = request.getParameter("status");
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "check-ins fetched", service.list(limit, offset, status), 200),
                response);
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser admin = AuthContext.get();
        UUID adminId = admin != null ? admin.getUserId() : null;

        String idParam = request.getParameter("checkinId");
        String action = request.getParameter("action");
        if (idParam == null || idParam.isBlank() || action == null || action.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "checkinId and action are required", null, 400), response);
            return;
        }
        UUID checkinId;
        try { checkinId = UUID.fromString(idParam); }
        catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid checkinId", null, 400), response);
            return;
        }

        // Read the JSON body once (PATCH carries note + purchaseAmount here).
        JsonNode body = readBody(request);

        boolean ok;
        if ("approve".equalsIgnoreCase(action)) {
            BigDecimal purchaseAmount;
            try {
                purchaseAmount = readPurchaseAmount(request, body);
            } catch (IllegalArgumentException e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
                return;
            }
            ok = service.approve(checkinId, adminId, purchaseAmount);
        } else if ("reject".equalsIgnoreCase(action)) {
            ok = service.reject(checkinId, adminId, readNote(request, body));
        } else {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "action must be approve or reject", null, 400), response);
            return;
        }

        if (!ok) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "check-in not found or already actioned", null, 409), response);
            return;
        }
        SendResponseUtil.sendResponse(new ApiResponse(true, "check-in " + action.toLowerCase() + "d", null, 200), response);
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

    /** Parse the request body once; returns null when there is no JSON body. */
    private JsonNode readBody(HttpServletRequest request) {
        try {
            return MAPPER.readTree(request.getInputStream());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String readNote(HttpServletRequest request, JsonNode body) {
        String q = request.getParameter("note");
        if (q != null && !q.isBlank()) return q;
        if (body != null && body.hasNonNull("note")) return body.get("note").asText();
        return null;
    }

    /**
     * The in-store spend (rupees) for an approved QR visit. Read from the
     * query string first, then the JSON body. Returns null when not supplied
     * (admin may approve without recording a spend -> legacy +1 point).
     * Throws IllegalArgumentException on a malformed or negative value.
     */
    private BigDecimal readPurchaseAmount(HttpServletRequest request, JsonNode body) {
        String raw = request.getParameter("purchaseAmount");
        if ((raw == null || raw.isBlank()) && body != null && body.hasNonNull("purchaseAmount")) {
            raw = body.get("purchaseAmount").asText();
        }
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("purchaseAmount must be >= 0");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("purchaseAmount must be a number");
        }
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return fallback; }
    }
}
