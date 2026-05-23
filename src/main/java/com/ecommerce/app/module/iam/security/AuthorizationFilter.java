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
        if (path != null && path.startsWith("/health")) {
            chain.doFilter(request, response);
            return;
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
