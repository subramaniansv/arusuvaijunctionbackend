package com.ecommerce.app.module.affiliate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Affiliate / referral endpoints.
 *
 *   GET  /api/affiliate/track?ref=CODE   -> (public) set aj_ref cookie (15d),
 *                                           count click, 302 to target path.
 *   POST /api/affiliate/links            -> (auth) create a shareable link
 *                                           body: { productId?, targetPath? }
 *   GET  /api/affiliate/links            -> (auth) my links + summary
 *
 * The cookie is httpOnly + SameSite=Lax so a top-level navigation from a shared
 * link carries it, but page scripts can't read/forge it. The code + first-click
 * timestamp are stored as "CODE|epochMillis" so the server can enforce the
 * 15-day window from the FIRST touch even if the cookie is later refreshed.
 */
@WebServlet(urlPatterns = { "/api/affiliate", "/api/affiliate/*" })
public class AffiliateController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AffiliateController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AffiliateService service = new AffiliateService();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String sub = request.getPathInfo();

        // --- public click tracking + redirect -------------------------------
        if ("/track".equals(sub)) {
            handleTrack(request, response);
            return;
        }

        // --- my links (auth) ------------------------------------------------
        if ("/links".equals(sub)) {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
                return;
            }
            try {
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "links fetched", service.myLinks(user.getUserId()), 200), response);
            } catch (Exception e) {
                LOG.error("exception at affiliate links fetch ", e);
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not fetch links", null, 500), response);
            }
            return;
        }

        SendResponseUtil.sendResponse(new ApiResponse(false, "unknown affiliate endpoint", null, 404), response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String sub = request.getPathInfo();
        if (!"/links".equals(sub)) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "unknown affiliate endpoint", null, 404), response);
            return;
        }
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "authentication required", null, 401), response);
            return;
        }
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            UUID productId = null;
            String targetPath = "/";
            if (body != null) {
                if (body.hasNonNull("productId")) {
                    try { productId = UUID.fromString(body.get("productId").asText()); }
                    catch (IllegalArgumentException e) {
                        SendResponseUtil.sendResponse(new ApiResponse(false, "invalid productId", null, 400), response);
                        return;
                    }
                }
                if (body.hasNonNull("targetPath")) targetPath = body.get("targetPath").asText();
            }
            var out = service.createLink(user.getUserId(), productId, targetPath);
            SendResponseUtil.sendResponse(new ApiResponse(true, "link created", out, 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at affiliate link create ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not create link", null, 500), response);
        }
    }

    // ---- /track handler -----------------------------------------------------

    private void handleTrack(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String code = request.getParameter("ref");
        String redirectBase = appHome();

        if (code == null || code.isBlank()) {
            response.sendRedirect(redirectBase + "/");
            return;
        }

        AffiliateLink link = service.track(code); // null if code invalid/inactive
        String target = (link != null && link.getTargetPath() != null) ? link.getTargetPath() : "/";

        if (link != null) {
            // First-touch wins: only set the cookie if not already present, so a
            // later click by the same browser doesn't reset the 15-day clock or
            // overwrite an earlier referrer.
            if (readRefCookie(request) == null) {
                long now = System.currentTimeMillis();
                String value = code + "|" + now;
                Cookie cookie = new Cookie(AffiliateService.REF_COOKIE,
                        URLEncoder.encode(value, StandardCharsets.UTF_8));
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                cookie.setMaxAge(service.windowDays() * 24 * 60 * 60);
                if (isSecure(request)) cookie.setSecure(true);
                // SameSite=Lax via header (Servlet Cookie has no setter pre-6.1).
                response.setHeader("Set-Cookie", buildSetCookieHeader(cookie, "Lax"));
            }
        }

        // Redirect to the SPA target page so the user lands where expected.
        response.sendRedirect(redirectBase + target);
    }

    // ---- cookie helpers (also used by the auth binding) ---------------------

    /** Returns "CODE|epochMillis" or null. Public so auth flow can reuse it. */
    public static String readRefCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (AffiliateService.REF_COOKIE.equals(c.getName())) {
                try { return java.net.URLDecoder.decode(c.getValue(), StandardCharsets.UTF_8); }
                catch (Exception e) { return c.getValue(); }
            }
        }
        return null;
    }

    /** Emit a Set-Cookie header clearing the ref cookie (used after binding). */
    public static String clearRefCookieHeader(HttpServletRequest request) {
        Cookie c = new Cookie(AffiliateService.REF_COOKIE, "");
        c.setPath("/");
        c.setMaxAge(0);
        c.setHttpOnly(true);
        if (isSecure(request)) c.setSecure(true);
        return buildSetCookieHeader(c, "Lax");
    }

    private static String buildSetCookieHeader(Cookie c, String sameSite) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName()).append('=').append(c.getValue() == null ? "" : c.getValue());
        sb.append("; Path=").append(c.getPath() == null ? "/" : c.getPath());
        sb.append("; Max-Age=").append(c.getMaxAge());
        if (c.isHttpOnly()) sb.append("; HttpOnly");
        if (c.getSecure()) sb.append("; Secure");
        sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }

    private static boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) return true;
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.equalsIgnoreCase("https");
    }

    private static String appHome() {
        String base = ENVConfig.get("APP_HOME_URL");
        if (base == null || base.isBlank()) base = ENVConfig.get("APP_BASE_URL");
        if (base == null || base.isBlank()) base = "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }
}
