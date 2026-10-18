package com.ecommerce.app.module.iam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

@WebFilter(filterName = "1_AuthorizationFilter", urlPatterns = "/*")
public class AuthorizationFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationFilter.class);

    // Endpoints under these prefixes are accessible without a JWT.
    // Everything else requires a valid bearer token; role/permission checks
    // are then enforced by @RequiresRole / @RequiresPermission annotations.
    public static final String[] public_path = { "/auth" };

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        LOG.info("filter activated");
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getServletPath();
        String httpMethod = request.getMethod();
        // /auth is public ONLY for POST (login / register / refresh).
        // Other HTTP methods on /auth (e.g. DELETE for logout) must be
        // authenticated so that the caller's identity comes from the JWT,
        // not from a client-supplied query string.
        if (isPublicPath(path) && "POST".equalsIgnoreCase(httpMethod)) {
            chain.doFilter(request, response);
            return;
        }
        // Health probes are always public (Kubernetes / LB / uptime monitors
        // call these without a token).
        if (path != null && (path.startsWith("/health") || path.equals("/ping") || path.equals("/sitemap-products.xml"))) {
            chain.doFilter(request, response);
            return;
        }
        // Web Push: the SPA must fetch the VAPID public key BEFORE the user
        // is asked for notification permission, which can happen while the
        // page is still anonymous. Only this one read is public; subscribe /
        // unsubscribe (POST) fall through and require a Bearer token.
        if ("GET".equalsIgnoreCase(httpMethod) && path != null
                && path.startsWith("/api/push")) {
            String pi = request.getPathInfo();
            if ("/vapidPublicKey".equals(pi)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // Public catalog browsing: GET /api/product (list, single, search,
        // recommendations) is open. Write operations on /api/product still
        // require a Bearer token and the usual role checks.
        if ("GET".equalsIgnoreCase(httpMethod) && path != null && path.startsWith("/api/product")) {
            chain.doFilter(request, response);
            return;
        }
        // Distinct category list powering the storefront filter sidebar.
        if ("GET".equalsIgnoreCase(httpMethod) && path != null && path.startsWith("/api/category")) {
            chain.doFilter(request, response);
            return;
        }
        // Public review browsing: GET /api/review?productId=<uuid> is open.
        // Submitting/deleting reviews still requires a Bearer token.
        if ("GET".equalsIgnoreCase(httpMethod) && path != null && path.startsWith("/api/review")) {
            chain.doFilter(request, response);
            return;
        }
        // Public festival-banner endpoints — no JWT required:
        //
        //   GET  /api/banner/festivals                        → active festival list
        //   GET  /api/banner/festivals/all/images             → ALL active images (home hero)
        //   GET  /api/banner/festivals/<slug>/images          → images for one festival
        //   GET  /api/banner/share/<slug>                     → shared message payload (view mode)
        //
        //   POST /api/banner/admin/messages                   → PUBLIC: user creates a greeting
        //        (no login needed — anyone on /festival can generate a shareable link)
        //
        // WHY fullPath: The BannerController is mounted on "/api/banner/*".
        // request.getServletPath() returns only "/api/banner" (the pattern prefix);
        // the remainder (e.g. "/festivals", "/share/abc") lives in getPathInfo().
        // We must concatenate both to get the complete path to match against.
        //
        // Admin sub-paths (/api/banner/admin/festivals, /api/banner/admin/images, etc.)
        // are NOT in this whitelist — they fall through to JWT + @RequiresRole below.
        if (path != null && path.startsWith("/api/banner")) {
            // Full URI path = servletPath + pathInfo
            // e.g. servletPath="/api/banner", pathInfo="/festivals/all/images"
            //   => fullPath = "/api/banner/festivals/all/images"
            String pathInfo = request.getPathInfo(); // null when no sub-path
            String fullPath = (pathInfo != null) ? path + pathInfo : path;
            String sub = fullPath.substring("/api/banner".length()); // e.g. "/festivals"

            // Public GETs — no token needed
            if ("GET".equalsIgnoreCase(httpMethod)) {
                boolean isPublicGet =
                        sub.equals("/festival")
                        || sub.equals("/festivals")
                        || sub.equals("/festivals/all/images")
                        || (sub.startsWith("/festivals/") && sub.endsWith("/images"))
                        || sub.startsWith("/share/");
                if (isPublicGet) {
                    chain.doFilter(request, response);
                    return;
                }
            }

            // Public POST — message creation (user on /festival page, no account needed)
            if ("POST".equalsIgnoreCase(httpMethod) && sub.equals("/admin/messages")) {
                chain.doFilter(request, response);
                return;
            }

            // Everything else (/admin/festivals, /admin/images, PUT, DELETE…) → JWT required
        }
        // Public affiliate click tracking: GET /api/affiliate/track?ref=CODE sets
        // the 15-day aj_ref cookie and 302-redirects to the shared target page.
        // No JWT — the visitor is usually anonymous at click time. Link mgmt
        // (POST/GET /api/affiliate/links) still requires a Bearer token.
        if ("GET".equalsIgnoreCase(httpMethod) && path != null
                && path.startsWith("/api/affiliate")) {
            String pathInfo = request.getPathInfo();
            if ("/track".equals(pathInfo)) {
                chain.doFilter(request, response);
                return;
            }
        }
        // Public contact form submissions. Listing / status updates / delete
        // on /api/contact still go through auth + @RequiresRole("Admin").
        if ("POST".equalsIgnoreCase(httpMethod) && path != null && path.startsWith("/api/contact")) {
            chain.doFilter(request, response);
            return;
        }
        // Public email verification - the user clicks a link in their inbox
        // before they have any session. POST /api/email-verify/resend still
        // requires a Bearer token (handled in the controller / @RequiresRole flow).
        if ("GET".equalsIgnoreCase(httpMethod) && path != null
                && path.equals("/api/email-verify")) {
            chain.doFilter(request, response);
            return;
        }
        // Public forgot-password endpoints - the user has no session because
        // they can't log in. POST /api/password-reset issues a reset link and
        // POST /api/password-reset/confirm consumes the single-use token.
        // Identity is proven by possession of the emailed token, not a JWT.
        if ("POST".equalsIgnoreCase(httpMethod) && path != null
                && (path.equals("/api/password-reset") || path.equals("/api/password-reset/confirm"))) {
            chain.doFilter(request, response);
            return;
        }
        // Public shipping availability & rate check. Customers need to check
        // serviceability/pricing before checkout (no login required).
        // Track action still requires auth (handled below).
        if ("GET".equalsIgnoreCase(httpMethod) && path != null
                && path.startsWith("/api/shipping")) {
            String action = request.getParameter("action");
            if ("check".equals(action) || "rate".equals(action)) {
                chain.doFilter(request, response);
                return;
            }
        }
        // Razorpay webhook: server-to-server callback from Razorpay's
        // infrastructure. No JWT possible. Authenticated INSIDE the
        // controller by HMAC-SHA256 over the raw request body using the
        // RAZORPAY_WEBHOOK_SECRET. Only POST /api/payment?action=webhook is
        // exempt - the initiate/verify actions remain authenticated.
        if ("POST".equalsIgnoreCase(httpMethod) && path != null
                && path.equals("/api/payment")
                && "webhook".equals(request.getParameter("action"))) {
            chain.doFilter(request, response);
            return;
        }

        // Rapido delivery status webhook: server-to-server callback. No JWT
        // possible. Authenticated INSIDE the controller by HMAC-SHA256 over the
        // raw request body using RAPIDO_WEBHOOK_SECRET.
        if ("POST".equalsIgnoreCase(httpMethod) && path != null
                && path.equals("/api/integrations/rapido/webhook")) {
            chain.doFilter(request, response);
            return;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "missing or invalivaild authorization header", null, 401), response);
            return;
        }
        String token = authHeader.substring(7);

        // -----------------------------------------------------------------
        // Phase 1: authentication + authorization checks.
        //
        // Any exception here (JWT parsing, missing servlet class, role
        // lookup) is a genuine auth failure -> respond 403.
        // -----------------------------------------------------------------
        AuthUser authUser;
        Class<?> servletClass;
        try {
            authUser = AuthUser.getAuthUser(token);
            String servletName = request.getHttpServletMapping().getServletName();
            servletClass = Class.forName(servletName);
            AuthContext.set(authUser);
            LOG.info("{}", (Object) authUser.getEmail());

            // class
            if (servletClass.isAnnotationPresent(RequiresRole.class)) {
                LOG.info("requires role noted");
                RequiresRole annotation = servletClass.getAnnotation(RequiresRole.class);
                if (!checkRole(authUser, annotation)) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "access denied for this user in role ", null, 403),
                            response);
                    return;
                }
            }
            if (servletClass.isAnnotationPresent(RequiresPermission.class)) {
                LOG.info("requires permission noted");
                RequiresPermission annotation = servletClass.getAnnotation(RequiresPermission.class);
                if (!authUser.hasPermission(annotation.resource(), annotation.action())) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "access denied for this user in permissions", null, 403),
                            response);
                    return;
                }
            }

            // method
            String servletMethod = resolveServletMethod(httpMethod);

            try {
                java.lang.reflect.Method method = servletClass.getMethod(
                        servletMethod,
                        HttpServletRequest.class,
                        HttpServletResponse.class);

                if (method.isAnnotationPresent(RequiresRole.class)) {
                    RequiresRole annotation = method.getAnnotation(RequiresRole.class);
                    if (!checkRole(authUser, annotation)) {
                        SendResponseUtil.sendResponse(
                                new ApiResponse(false, "Access denied insufficient role on method", null, 403),
                                response);
                        return;
                    }
                }

                if (method.isAnnotationPresent(RequiresPermission.class)) {
                    RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
                    if (!authUser.hasPermission(annotation.resource(), annotation.action())) {
                        SendResponseUtil.sendResponse(
                                new ApiResponse(false, "Access denied insufficient permission on method", null, 403),
                                response);
                        return;
                    }
                }

            } catch (NoSuchMethodException e) {
                LOG.info("{}", (Object) "No method " + servletMethod + " skipping method level check");
            }

        } catch (Exception e) {
            // Auth-layer failure ONLY. Business exceptions from chain.doFilter
            // below are intentionally NOT caught here -- they propagate to
            // GlobalExceptionFilter so we don't mis-label a 500 as a 403.
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "access denied for this user in exception occured", null, 403),
                    response);
            LOG.error("exception ", e);
            AuthContext.clear();
            return;
        }

        // -----------------------------------------------------------------
        // Phase 2: hand off to the servlet. Exceptions from here propagate
        // up to GlobalExceptionFilter -> uniform 500 envelope.
        // AuthContext is always cleared in finally so threadlocals never
        // leak between requests on the same thread.
        // -----------------------------------------------------------------
        try {
            chain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }

    }

    private boolean checkRole(AuthUser user, RequiresRole annotation) {
        return annotation.matchAll() ? user.hasAllRoles(annotation.value()) : user.hasAnyRoles(annotation.value());
    }

    private boolean isPublicPath(String path) {
        for (String p : public_path) {
            if (path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private String resolveServletMethod(String httpMethod) {
        switch (httpMethod.toUpperCase()) {
            case "GET":
                return "doGet";
            case "POST":
                return "doPost";
            case "PUT":
                return "doPut";
            case "DELETE":
                return "doDelete";
            case "PATCH":
                return "doPatch";
            default:
                return "doGet";
        }
    }

}
