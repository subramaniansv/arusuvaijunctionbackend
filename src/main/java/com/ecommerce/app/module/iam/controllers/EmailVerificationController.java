package com.ecommerce.app.module.iam.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.services.EmailVerificationService;
import com.ecommerce.app.module.iam.services.EmailVerificationService.Result;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Email verification endpoints.
 *
 *   GET  /api/email-verify?token=<raw>   public  - renders an HTML status page.
 *   POST /api/email-verify/resend        bearer  - regenerates + re-sends the link
 *                                                  for the currently signed-in user.
 *
 * The GET endpoint deliberately returns HTML rather than JSON because the
 * link is clicked in an email client - the browser lands directly on this
 * URL and the user expects a human-readable confirmation page, not raw JSON.
 */
@WebServlet({"/api/email-verify", "/api/email-verify/resend"})
public class EmailVerificationController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(EmailVerificationController.class);

    private final EmailVerificationService service = new EmailVerificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (path.endsWith("/resend")) {
            // GET is not supported on /resend - tell the caller.
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "use POST to resend", null, 405), response);
            return;
        }

        String token = request.getParameter("token");
        Result result;
        try {
            result = service.verify(token);
        } catch (Exception e) {
            LOG.error("exception at email verify ", e);
            result = Result.INVALID;
        }

        response.setContentType("text/html; charset=UTF-8");
        switch (result) {
            case VERIFIED -> {
                response.setStatus(HttpServletResponse.SC_OK);
                writeHtml(response,
                        "Email verified",
                        "#0f5d3a",
                        "&#x2714; Your email has been verified",
                        "Thanks - your Arusuvai account is now fully active. You can close this tab and return to the app.");
            }
            case ALREADY_USED -> {
                response.setStatus(HttpServletResponse.SC_OK);
                writeHtml(response,
                        "Already verified",
                        "#0f5d3a",
                        "Email already verified",
                        "This link has already been used. Your account is good to go.");
            }
            case EXPIRED -> {
                response.setStatus(HttpServletResponse.SC_GONE);
                writeHtml(response,
                        "Link expired",
                        "#b45309",
                        "This verification link has expired",
                        "Sign in to your account and request a fresh verification email.");
            }
            case INVALID -> {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeHtml(response,
                        "Invalid link",
                        "#b91c1c",
                        "We couldn&#39;t verify that link",
                        "The link is incomplete or invalid. Try requesting a new one from your account.");
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI() == null ? "" : request.getRequestURI();
        if (!path.endsWith("/resend")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "not found", null, 404), response);
            return;
        }

        // Resend is bearer-only - the AuthorizationFilter already verified the
        // JWT for non-public paths, so AuthContext is set. Defence-in-depth:
        // bail out if it isn't.
        AuthUser caller = AuthContext.get();
        if (caller == null || caller.getUserId() == null) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "unauthorized", null, 401), response);
            return;
        }
        try {
            boolean ok = service.resendForUser(caller.getUserId());
            if (ok) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "verification email sent", null, 200), response);
            } else {
                // Either the user is already verified or no email on file.
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "verification not sent (already verified or no email)", null, 400),
                        response);
            }
        } catch (Exception e) {
            LOG.error("exception at email verify resend ", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "could not resend verification email", null, 500), response);
        }
    }

    private void writeHtml(HttpServletResponse response,
                           String pageTitle, String accent,
                           String heading, String body) throws IOException {
        String home = ENVConfig.get("APP_HOME_URL");
        if (home == null || home.isBlank()) home = "/";
        String html = "<!doctype html><html lang='en'><head>"
                + "<meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + pageTitle + " - Arusuvai</title>"
                + "<style>"
                + "body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;"
                + "background:#fafaf7;color:#1a1a1a;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:24px;}"
                + ".card{max-width:480px;width:100%;background:#fff;border:1px solid #eceae3;border-radius:16px;"
                + "padding:36px 32px;text-align:center;box-shadow:0 6px 24px rgba(15,93,58,0.06);}"
                + ".badge{display:inline-block;padding:6px 14px;border-radius:999px;font-size:12px;font-weight:600;"
                + "letter-spacing:.08em;text-transform:uppercase;background:" + accent + "1a;color:" + accent + ";margin-bottom:18px;}"
                + "h1{margin:0 0 12px;font-size:22px;color:" + accent + ";}"
                + "p{margin:0 0 20px;line-height:1.6;color:#4b5563;font-size:15px;}"
                + ".cta{display:inline-block;padding:11px 22px;background:#0f5d3a;color:#fff;text-decoration:none;"
                + "border-radius:999px;font-weight:600;font-size:14px;}"
                + ".footer{margin-top:28px;color:#9ca3af;font-size:12px;}"
                + "</style></head><body><main class='card'>"
                + "<span class='badge'>Arusuvai</span>"
                + "<h1>" + heading + "</h1>"
                + "<p>" + body + "</p>"
                + "<a class='cta' href='" + home + "'>Go to homepage</a>"
                + "<div class='footer'>Authentic South-Indian flavours</div>"
                + "</main></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
