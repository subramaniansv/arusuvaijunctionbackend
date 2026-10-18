package com.ecommerce.app.module.loyalty;

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
 * Customer-facing point redemption. Requires a JWT (enforced by the filter).
 *
 *   GET  /api/loyalty/redeem/settings            -> { pointValueInr }
 *   GET  /api/loyalty/redeem/quote?points=&total= -> clamped discount preview
 *   GET  /api/loyalty/redeem?limit=&offset=       -> my redemption history
 *   POST /api/loyalty/redeem/offline  body {points, note?}
 *                                                -> reserve points, await admin approval
 *
 * (Online redemption happens inside the payment flow, not here.)
 */
@WebServlet(urlPatterns = { "/api/loyalty/redeem", "/api/loyalty/redeem/*" })
public class LoyaltyRedemptionController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyRedemptionController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LoyaltyRedemptionService service = new LoyaltyRedemptionService();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        String sub = request.getPathInfo();
        if ("/settings".equals(sub)) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "loyalty settings", service.settings(), 200), response);
            return;
        }
        if ("/quote".equals(sub)) {
            int points = parseInt(request.getParameter("points"), 0);
            double total = parseDouble(request.getParameter("total"), 0);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "redeem quote",
                            service.quote(user.getUserId(), points, total), 200),
                    response);
            return;
        }
        // Base path: my redemption history.
        int limit = parseInt(request.getParameter("limit"), 20);
        int offset = parseInt(request.getParameter("offset"), 0);
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "redemptions fetched",
                        service.myRedemptions(user.getUserId(), limit, offset), 200),
                response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        String sub = request.getPathInfo();
        if (!"/offline".equals(sub)) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unknown loyalty action", null, 404), response);
            return;
        }
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            int points = body != null && body.hasNonNull("points") ? body.get("points").asInt(0) : 0;
            String note = body != null && body.hasNonNull("note") ? body.get("note").asText() : null;
            LoyaltyRedemption r = service.requestOffline(user.getUserId(), points, note);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "redemption requested — awaiting staff approval", r, 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at loyalty redeem offline ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not request redemption", null, 500), response);
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return fallback; }
    }
}
