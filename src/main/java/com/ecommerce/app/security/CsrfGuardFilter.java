package com.ecommerce.app.security;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Origin-based CSRF guard for state-changing requests.
 *
 * <p>This API is stateless and authenticates with a {@code Authorization:
 * Bearer <jwt>} header (tokens live in the SPA's storage, never in cookies),
 * so it is largely immune to classic cookie-driven CSRF. This filter adds a
 * second, independent layer: for unsafe HTTP methods (POST/PUT/PATCH/DELETE)
 * it verifies that any browser-supplied {@code Origin}/{@code Referer} belongs
 * to the configured allowlist and rejects the request otherwise.</p>
 *
 * <p>Why this matters even with the Tomcat {@code CorsFilter}: CorsFilter only
 * decides whether to <em>add</em> CORS response headers. A cross-site "simple"
 * request still reaches the servlet and can mutate state — the browser merely
 * hides the response. Blocking here stops the mutation server-side.</p>
 *
 * <p>Requests with no {@code Origin} and no {@code Referer} (server-to-server
 * calls, webhooks, curl, mobile clients) are allowed through: a forged
 * cross-site browser request always carries one of these headers, so their
 * absence is not a CSRF signal. Identity is still enforced downstream by the
 * JWT check / webhook HMAC.</p>
 *
 * <p>Configuration (init-params):</p>
 * <ul>
 *   <li>{@code csrf.allowed.origins} — comma-separated origin allowlist
 *       (e.g. {@code https://www.arusuvaijunction.com,http://localhost:5173}),
 *       or {@code *} to disable origin enforcement.</li>
 * </ul>
 */
public class CsrfGuardFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(CsrfGuardFilter.class);

    private static final Set<String> SAFE_METHODS =
            Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private Set<String> allowedOrigins = Collections.emptySet();
    private boolean enforce = true;

    @Override
    public void init(FilterConfig config) {
        String raw = config.getInitParameter("csrf.allowed.origins");
        if (raw == null || raw.isBlank() || "*".equals(raw.trim())) {
            enforce = false;
            LOG.warn("CsrfGuardFilter: origin enforcement DISABLED (csrf.allowed.origins='{}'). "
                    + "Set an explicit allowlist in production.", raw);
            return;
        }
        Set<String> set = new LinkedHashSet<>();
        for (String o : raw.split(",")) {
            String trimmed = o.trim();
            if (!trimmed.isEmpty()) {
                set.add(stripTrailingSlash(trimmed.toLowerCase()));
            }
        }
        allowedOrigins = Collections.unmodifiableSet(set);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!enforce || SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(req, res);
            return;
        }

        String origin = originOf(request);
        // No browser-supplied origin -> not a cross-site browser request.
        // (Server-to-server callers, webhooks, native clients.)
        if (origin == null) {
            chain.doFilter(req, res);
            return;
        }

        if (allowedOrigins.contains(origin)) {
            chain.doFilter(req, res);
            return;
        }

        LOG.warn("CSRF guard blocked {} {} from disallowed origin '{}'",
                request.getMethod(), request.getRequestURI(), origin);
        SendResponseUtil.sendResponse(
                new ApiResponse(false, "Request origin not allowed", null, 403), response);
    }

    /**
     * Resolve the request's web origin from the {@code Origin} header, falling
     * back to the scheme+host+port of the {@code Referer}. Returns null when
     * neither is present.
     */
    private static String originOf(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !"null".equalsIgnoreCase(origin.trim())) {
            return stripTrailingSlash(origin.trim().toLowerCase());
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URI u = java.net.URI.create(referer.trim());
                if (u.getScheme() != null && u.getHost() != null) {
                    StringBuilder sb = new StringBuilder()
                            .append(u.getScheme().toLowerCase()).append("://")
                            .append(u.getHost().toLowerCase());
                    if (u.getPort() != -1) {
                        sb.append(':').append(u.getPort());
                    }
                    return sb.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed Referer -> treat as no origin signal.
            }
        }
        return null;
    }

    private static String stripTrailingSlash(String s) {
        return (s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
