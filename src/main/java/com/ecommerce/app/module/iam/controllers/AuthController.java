package com.ecommerce.app.module.iam.controllers;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.ecommerce.app.module.iam.mapper.UserConverterUtil;
import com.ecommerce.app.module.iam.models.*;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.services.AuthService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

@WebServlet("/auth")
public class AuthController extends HttpServlet {
    AuthService service = new AuthService();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        boolean isRefresh = request.getParameter("isRefresh") != null;

        if (isRefresh) {
            // Refresh token must be sent in the request body, not the URL,
            // so it doesn't leak into access logs / Referer headers / history.
            String refreshToken = readTokenFromBody(request);
            if (refreshToken == null || refreshToken.isBlank()) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "refresh token required in body", null, 400), response);
                return;
            }
            try {
                TokenResponse tokenResponse = service.refreshAccessToken(refreshToken);
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "token refreshed", tokenResponse, 200), response);
            } catch (Exception e) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, e.getMessage(), null, 401), response);
            }
            return;
        }

        // Sign in with Google: body is { "credential": "<google id token>" }.
        boolean isGoogle = Boolean.parseBoolean(request.getParameter("isGoogle"));
        if (isGoogle) {
            String credential = readFieldFromBody(request, "credential");
            if (credential == null || credential.isBlank()) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "google credential required", null, 400), response);
                return;
            }
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setIpAddress(request.getRemoteAddr());
            refreshToken.setUserAgent(request.getHeader("User-Agent"));
            try {
                TokenResponse tokenResponse = service.loginWithGoogle(credential, refreshToken);
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "user logged in", tokenResponse, 200), response);
            } catch (Exception e) {
                String reason = (e.getMessage() == null || e.getMessage().isBlank())
                        ? "google sign-in failed" : e.getMessage();
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, reason, null, 401), response);
            }
            return;
        }

        // Register / login.
        User user = UserConverterUtil.requestToDto(request);
        if (user == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid payload", null, 400), response);
            return;
        }
        String userAgent = request.getHeader("User-Agent");
        String ipAddress = request.getRemoteAddr();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent);
        boolean isLogin = Boolean.parseBoolean(request.getParameter("isLogin"));
        if (!isLogin) {
            try {
                TokenResponse tokenResponse = service.register(user, refreshToken);
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "user registered", tokenResponse, 200), response);
            } catch (Exception e) {
                // Surface the actual reason in `message` so the client can show
                // it directly (e.g. "user not registered (email may already
                // exist)") instead of a generic string.
                String reason = (e.getMessage() == null || e.getMessage().isBlank())
                        ? "user not registered" : e.getMessage();
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, reason, null, 400), response);
            }
        } else {
            try {
                TokenResponse tokenResponse = service.login(user, refreshToken);
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "user logged in", tokenResponse, 200), response);
            } catch (Exception e) {
                // 401 is the correct status for invalid/missing credentials.
                // Put the real reason in `message` (e.g. "invalid credentials",
                // "user not available", "user status is SUSPENDED").
                String reason = (e.getMessage() == null || e.getMessage().isBlank())
                        ? "user not logged in" : e.getMessage();
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, reason, null, 401), response);
            }
        }
    }

    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // AuthorizationFilter enforces auth for non-POST /auth requests, so by
        // the time we get here AuthContext must be populated. The userId is
        // taken from the JWT — never trust a client-supplied userId on logout.
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unauthorized", null, 401), response);
            return;
        }

        boolean revokeAll = "true".equalsIgnoreCase(request.getParameter("all"));
        if (revokeAll) {
            try {
                service.deleteAll(caller.getUserId().toString());
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "all refresh tokens revoked", null, 200), response);
            } catch (Exception e) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, e.getMessage(), null, 400), response);
            }
            return;
        }

        String refreshToken = readTokenFromBody(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "refresh token required in body", null, 400), response);
            return;
        }
        try {
            service.deleteByRefreshId(refreshToken, caller.getUserId());
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "logged out", null, 200), response);
        } catch (Exception e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, e.getMessage(), null, 400), response);
        }
    }

    private String readTokenFromBody(HttpServletRequest request) {
        return readFieldFromBody(request, "token");
    }

    private String readFieldFromBody(HttpServletRequest request, String field) {
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body != null && body.hasNonNull(field)) {
                return body.get(field).asText();
            }
        } catch (Exception e) {
            // fall through — caller treats null as missing
        }
        return null;
    }

}
