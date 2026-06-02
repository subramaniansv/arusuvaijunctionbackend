package com.ecommerce.app.security;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Adds a baseline of HTTP security response headers to every response.
 *
 * <p>These are cheap, broadly-supported defences that harden the API against
 * MIME sniffing, clickjacking, referrer leakage, and (when served over TLS)
 * protocol downgrade attacks. All values are sensible defaults for a JSON API
 * that is consumed by a separate single-page-app frontend.</p>
 *
 * <p>Configuration (init-params, all optional):</p>
 * <ul>
 *   <li>{@code security.csp}          — Content-Security-Policy value. Defaults to a
 *       locked-down policy that is safe for a pure JSON/XML API.</li>
 *   <li>{@code security.hsts}         — "true"/"false". When true, emits
 *       Strict-Transport-Security on requests that arrive over HTTPS
 *       (directly or via {@code X-Forwarded-Proto: https}). Default "false"
 *       so local HTTP development is never pinned to HTTPS.</li>
 *   <li>{@code security.hsts.maxage}  — HSTS max-age in seconds. Default 31536000 (1y).</li>
 *   <li>{@code security.frame.options}— X-Frame-Options value. Default "DENY".</li>
 *   <li>{@code security.referrer.policy} — Referrer-Policy. Default
 *       "strict-origin-when-cross-origin".</li>
 *   <li>{@code security.permissions.policy} — Permissions-Policy. Default disables
 *       camera/microphone/geolocation.</li>
 * </ul>
 */
public class SecurityHeadersFilter implements Filter {

    private String csp;
    private boolean hstsEnabled;
    private String hstsValue;
    private String frameOptions;
    private String referrerPolicy;
    private String permissionsPolicy;

    @Override
    public void init(FilterConfig config) {
        csp = param(config, "security.csp",
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
        hstsEnabled = Boolean.parseBoolean(param(config, "security.hsts", "false"));
        String maxAge = param(config, "security.hsts.maxage", "31536000");
        hstsValue = "max-age=" + maxAge + "; includeSubDomains";
        frameOptions = param(config, "security.frame.options", "DENY");
        referrerPolicy = param(config, "security.referrer.policy", "strict-origin-when-cross-origin");
        permissionsPolicy = param(config, "security.permissions.policy",
                "camera=(), microphone=(), geolocation=(), browsing-topics=()");
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Stop browsers from MIME-sniffing a response away from its declared type.
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Disallow embedding the API in frames (clickjacking defence-in-depth).
        response.setHeader("X-Frame-Options", frameOptions);
        // Limit how much URL information leaks via the Referer header.
        response.setHeader("Referrer-Policy", referrerPolicy);
        // Switch off powerful browser features we never use.
        response.setHeader("Permissions-Policy", permissionsPolicy);
        // Isolate this origin's browsing context group.
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // CSP is harmless on JSON/XML and blocks any accidental HTML injection.
        if (csp != null && !csp.isBlank()) {
            response.setHeader("Content-Security-Policy", csp);
        }

        // Only advertise HSTS over a secure channel; never over plain HTTP
        // (otherwise a one-off local HTTP hit could pin the browser to HTTPS).
        if (hstsEnabled && isSecure(request)) {
            response.setHeader("Strict-Transport-Security", hstsValue);
        }

        chain.doFilter(req, res);
    }

    /** True when the request reached us over TLS, directly or via a proxy. */
    private static boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        return proto != null && proto.toLowerCase().contains("https");
    }

    private static String param(FilterConfig config, String name, String fallback) {
        String v = config.getInitParameter(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
