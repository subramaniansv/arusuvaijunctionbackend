package com.ecommerce.app.module.iam.controllers;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.services.PasswordResetService;
import com.ecommerce.app.module.iam.services.PasswordResetService.Result;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Public forgot-password endpoints (no JWT required - the user has no session
 * because they can't log in).
 *
 *   POST /api/password-reset            body: { "email": "<email>" }
 *       Issue a reset link by email. Always returns a generic 200 so the
 *       endpoint can't be used to probe which emails are registered.
 *
 *   POST /api/password-reset/confirm    body: { "token": "<raw>", "newPassword": "<pw>" }
 *       Consume the single-use token and set the new password. No old password
 *       is required - possession of a valid token proves identity.
 *
 * Both paths are whitelisted in AuthorizationFilter for POST only.
 */
@WebServlet({"/api/password-reset", "/api/password-reset/confirm"})
public class PasswordResetController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PasswordResetService service = new PasswordResetService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (path.endsWith("/confirm")) {
            confirm(request, response);
        } else {
            requestReset(request, response);
        }
    }

    /** POST /api/password-reset - email a reset link. */
    private void requestReset(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        JsonNode body = readBody(request);
        String email = field(body, "email");
        try {
            service.requestReset(email);
        } catch (Exception e) {
            // Log but still return the generic response - never leak failures
            // that could reveal whether the email exists.
            LOG.error("exception at password reset request ", e);
        }
        // Always the same response regardless of whether the email exists.
        SendResponseUtil.sendResponse(
                new ApiResponse(true,
                        "If an account exists for that email, a reset link has been sent.",
                        null, 200),
                response);
    }

    /** POST /api/password-reset/confirm - consume token + set new password. */
    private void confirm(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        JsonNode body = readBody(request);
        String token = field(body, "token");
        String newPassword = field(body, "newPassword");

        Result result;
        try {
            result = service.reset(token, newPassword);
        } catch (Exception e) {
            LOG.error("exception at password reset confirm ", e);
            result = Result.INVALID;
        }

        switch (result) {
            case RESET -> SendResponseUtil.sendResponse(
                    new ApiResponse(true, "password reset", null, 200), response);
            case WEAK_PASSWORD -> SendResponseUtil.sendResponse(
                    new ApiResponse(false, "newPassword must be at least 6 characters", null, 400), response);
            case EXPIRED -> SendResponseUtil.sendResponse(
                    new ApiResponse(false, "this reset link has expired", null, 410), response);
            case ALREADY_USED -> SendResponseUtil.sendResponse(
                    new ApiResponse(false, "this reset link has already been used", null, 410), response);
            case INVALID -> SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid or incomplete reset link", null, 400), response);
        }
    }

    /** Parse the JSON request body once - the input stream can only be read once. */
    private JsonNode readBody(HttpServletRequest request) {
        try {
            return MAPPER.readTree(request.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    private String field(JsonNode body, String name) {
        if (body != null && body.hasNonNull(name)) {
            return body.get(name).asText();
        }
        return null;
    }
}
