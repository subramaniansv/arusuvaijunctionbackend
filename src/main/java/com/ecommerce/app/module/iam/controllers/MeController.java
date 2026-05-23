package com.ecommerce.app.module.iam.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.services.UserService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Self-service endpoints for the authenticated caller.
 *
 *   GET  /api/me           - return the caller's own profile (no password hash)
 *   PUT  /api/me           - change the caller's own password
 *                            body: { "oldPassword": "...", "newPassword": "..." }
 *
 * Critical: the user identity for both methods comes from AuthContext (set by
 * AuthorizationFilter from the JWT). The request body's userId is NEVER read,
 * so a logged-in user can only ever read/modify their OWN account.
 *
 * No @RequiresRole annotation - JWT validation alone is enough; any
 * authenticated user can hit /api/me.
 */
@WebServlet("/api/me")
public class MeController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(MeController.class);


    private final UserService service = new UserService();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), response);
            return;
        }
        try {
            User user = service.getOwnProfile(caller.getUserId());
            if (user == null || user.getId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "user not found", null, 404), response);
                return;
            }
            SendResponseUtil.sendResponse(new ApiResponse(true, "profile fetched", user, 200), response);
        } catch (Exception e) {
            LOG.error("exception at MeController doGet  ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not fetch profile", null, 500), response);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthenticated", null, 401), response);
            return;
        }

        String oldPassword = null;
        String newPassword = null;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body != null) {
                if (body.hasNonNull("oldPassword")) {
                    oldPassword = body.get("oldPassword").asText();
                }
                if (body.hasNonNull("newPassword")) {
                    newPassword = body.get("newPassword").asText();
                }
            }
        } catch (Exception parseErr) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid password payload", null, 400), response);
            return;
        }

        try {
            boolean ok = service.updatePassword(caller.getUserId(), oldPassword, newPassword);
            if (!ok) {
                // Repository returns false when the old-password verification
                // fails. Use 401 so the client can show "wrong current password".
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "current password is incorrect", null, 401), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "password updated", null, 200), response);
        } catch (RuntimeException re) {
            // Service throws on validation errors (missing fields, too short,
            // same as old).
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, re.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at MeController doPut  ", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not update password", null, 500), response);
        }
    }
}
