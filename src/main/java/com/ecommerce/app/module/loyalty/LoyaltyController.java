package com.ecommerce.app.module.loyalty;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Customer-facing loyalty endpoints. Requires a valid JWT (enforced by the
 * AuthorizationFilter — the QR deep-link lands on the SPA which, if the user is
 * unauthenticated, routes them through /login first and then re-issues the scan).
 *
 *   POST /api/loyalty/scan   -> raise a PENDING check-in for today (QR scan)
 *   GET  /api/loyalty/card   -> the caller's card summary + visit history
 */
@WebServlet("/api/loyalty")
public class LoyaltyController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyController.class);
    private final LoyaltyService service = new LoyaltyService();

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        String sub = request.getPathInfo(); // "/scan" when mounted on /api/loyalty/*
        if (sub == null || sub.equals("/") || sub.equals("/scan")) {
            try {
                var result = service.scan(user.getUserId());
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, String.valueOf(result.getOrDefault("message", "ok")), result, 200),
                        response);
            } catch (IllegalArgumentException e) {
                SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
            } catch (Exception e) {
                LOG.error("exception at loyalty scan ", e);
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not check in", null, 500), response);
            }
            return;
        }
        SendResponseUtil.sendResponse(new ApiResponse(false, "unknown loyalty action", null, 404), response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        try {
            var card = service.myCard(user.getUserId());
            SendResponseUtil.sendResponse(new ApiResponse(true, "loyalty card fetched", card, 200), response);
        } catch (Exception e) {
            LOG.error("exception at loyalty card ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not fetch loyalty card", null, 500), response);
        }
    }
}
