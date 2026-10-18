package com.ecommerce.app;

import java.io.File;
import java.nio.file.Files;

import org.apache.catalina.Context;
import org.apache.catalina.filters.CorsFilter;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.config.DBLifecycleListener;
import com.ecommerce.app.module.admin.AdminController;
import com.ecommerce.app.module.cart.CartController;
import com.ecommerce.app.module.wishlist.WishlistController;
import com.ecommerce.app.module.push.PushController;
import com.ecommerce.app.module.mail.MailController;
import com.ecommerce.app.module.health.HealthController;
import com.ecommerce.app.module.health.PingController;
import com.ecommerce.app.module.iam.controllers.AuthController;
import com.ecommerce.app.module.iam.controllers.EmailVerificationController;
import com.ecommerce.app.module.iam.controllers.MeController;
import com.ecommerce.app.module.iam.controllers.PasswordResetController;
import com.ecommerce.app.module.iam.controllers.PermissionContoller;
import com.ecommerce.app.module.iam.controllers.RoleController;
import com.ecommerce.app.module.iam.controllers.RoleMappingController;
import com.ecommerce.app.module.iam.controllers.UserContoller;
import com.ecommerce.app.module.iam.security.AuthorizationFilter;
import com.ecommerce.app.module.order.InvoiceController;
import com.ecommerce.app.module.order.OrderController;
import com.ecommerce.app.module.payment.PaymentController;
import com.ecommerce.app.module.product.CategoryController;
import com.ecommerce.app.module.product.ProductController;
import com.ecommerce.app.module.product.ProductImageController;
import com.ecommerce.app.module.product.ProductVariantController;
import com.ecommerce.app.module.address.UserAddressController;
import com.ecommerce.app.module.banner.BannerController;
import com.ecommerce.app.module.contact.ContactController;
import com.ecommerce.app.module.review.ReviewController;
import com.ecommerce.app.module.search.ProductSearchController;
import com.ecommerce.app.module.search.ProductSearchIndexer;
import com.ecommerce.app.module.shipping.ShippingController;
import com.ecommerce.app.module.shipping.AdminShippingController;
import com.ecommerce.app.module.quick.QuickDeliveryController;
import com.ecommerce.app.module.quick.AdminQuickController;
import com.ecommerce.app.module.delivery.RapidoWebhookController;
import com.ecommerce.app.module.loyalty.LoyaltyController;
import com.ecommerce.app.module.loyalty.AdminLoyaltyController;
import com.ecommerce.app.module.affiliate.AffiliateController;
import com.ecommerce.app.module.affiliate.AdminAffiliateController;
import com.ecommerce.app.security.GlobalExceptionFilter;
import com.ecommerce.app.security.SecurityHeadersFilter;
import com.ecommerce.app.security.CsrfGuardFilter;
import com.ecommerce.app.security.CharacterEncodingFilter;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;

/**
 * Application entry point. Boots embedded Tomcat 10, wires every servlet,
 * filter, and listener explicitly (no annotation scanning), and blocks the
 * main thread on {@code tomcat.getServer().await()}.
 *
 * <p>Configuration via env vars:</p>
 * <ul>
 *   <li>{@code PORT}                 — HTTP port (default 8080)</li>
 *   <li>{@code CONTEXT_PATH}         — webapp context path (default "/arusuvai")</li>
 *   <li>{@code CORS_ALLOWED_ORIGINS} — comma-separated list, or "*". When unset,
 *       a safe default is derived from APP_HOME_URL/APP_BASE_URL + localhost dev
 *       origins (no longer "*").</li>
 *   <li>{@code CORS_ALLOW_CREDENTIALS} — "true"/"false" (default "false";
 *       forced off when origins is "*", as required by the CORS spec)</li>
 *   <li>{@code ENABLE_HSTS}           — "true"/"false" (default "false"). Emits
 *       Strict-Transport-Security on HTTPS requests when true.</li>
 *   <li>{@code CONTENT_SECURITY_POLICY} — override the default API CSP.</li>
 * </ul>
 *
 * <p>Run with: {@code java -jar target/ecommerce.jar}</p>
 */
public final class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int port = parsePort(System.getenv("PORT"), 8080);
        String contextPath = envOrDefault("CONTEXT_PATH", "/arusuvai");

        // Tomcat needs a writable scratch directory.
        File baseDir = Files.createTempDirectory("arusuvai-tomcat").toFile();
        baseDir.deleteOnExit();

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.getAbsolutePath());
        tomcat.setPort(port);
        tomcat.getConnector(); // force connector creation

        // An empty docBase works fine because we register everything by hand.
        File docBase = new File(baseDir, "webapp");
        if (!docBase.mkdirs() && !docBase.isDirectory()) {
            throw new IllegalStateException("Could not create docBase: " + docBase);
        }
        Context ctx = tomcat.addContext(contextPath, docBase.getAbsolutePath());

        registerListeners(ctx);
        registerFilters(ctx);
        registerServlets(ctx);

        tomcat.start();
        LOG.info("Arusuvai listening on http://localhost:{}{}", port, contextPath);
        LOG.info("Health probe: http://localhost:{}{}/health", port, contextPath);
        LOG.info("APP_BASE_URL  = {}", com.ecommerce.app.module.iam.config.ENVConfig.get("APP_BASE_URL"));
        LOG.info("APP_HOME_URL  = {}", com.ecommerce.app.module.iam.config.ENVConfig.get("APP_HOME_URL"));

        // One-time migration: backfill variant_label on legacy order_items
        com.ecommerce.app.module.order.FixVariantMigration.run();

        // One-time migration: festival spotlight (image_id, sender_name, single-active rule)
        com.ecommerce.app.module.banner.FestivalSpotlightMigration.run();

        // One-time migration: loyalty card (QR check-in) tables
        com.ecommerce.app.module.loyalty.LoyaltyMigration.run();

        // One-time migration: loyalty point redemption (settings + ledger)
        com.ecommerce.app.module.loyalty.LoyaltyPointsMigration.run();

        // One-time migration: affiliate / referral (links, attributions, conversions, rates)
        com.ecommerce.app.module.affiliate.AffiliateMigration.run();

        // One-time migration: web-push subscriptions (one row per device)
        com.ecommerce.app.module.push.PushMigration.run();

        // Best-effort Elasticsearch bootstrap: create the index + alias
        // and seed it from Postgres. Runs in the background so a missing
        // ES instance never blocks startup; search falls back to PG.
        Thread esBoot = new Thread(() -> {
            try {
                ProductSearchIndexer indexer = new ProductSearchIndexer();
                indexer.ensureIndex();
                int n = indexer.reindexAll();
                if (n > 0) LOG.info("ES warm-up: indexed {} products on startup", n);
            } catch (Exception e) {
                LOG.warn("ES warm-up failed: {}", e.getMessage());
            }
        }, "es-bootstrap");
        esBoot.setDaemon(true);
        esBoot.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                LOG.warn("error stopping tomcat", e);
            }
        }, "tomcat-shutdown"));

        tomcat.getServer().await();
    }

    // --- registration helpers ----------------------------------------------

    private static void registerListeners(Context ctx) {
        // DBLifecycleListener warms HikariCP on startup and shuts it down on
        // context destroy. We add it by class name so Tomcat instantiates it.
        ctx.addApplicationListener(DBLifecycleListener.class.getName());
    }

    /**
     * Filter chain order (top = runs first):
     *   1. CORS              — answers OPTIONS preflight, adds Access-Control-* headers
     *   2. SecurityHeaders   — stamps hardening headers (nosniff, frame-deny, CSP, HSTS…)
     *   3. GlobalException   — turns uncaught throwables into JSON 500 responses
     *   4. CsrfGuard         — rejects unsafe methods from disallowed browser origins
     *   5. Authorization     — JWT bearer-token check + @RequiresRole / @RequiresPermission
     */
    private static void registerFilters(Context ctx) {
        // Single source of truth for which web origins may talk to this API.
        // Shared by both the CORS layer (response headers) and the CSRF guard
        // (request-time origin enforcement) so they can never drift apart.
        String allowedOrigins = corsAllowedOrigins();
        boolean allowCredentials = Boolean.parseBoolean(
                envOrDefault("CORS_ALLOW_CREDENTIALS", "false"));

        if ("*".equals(allowedOrigins.trim())) {
            LOG.warn("CORS_ALLOWED_ORIGINS is '*': every site can call this API. "
                    + "Set an explicit comma-separated allowlist in production.");
            // A wildcard origin can never be combined with credentials per the
            // CORS spec; force it off so the CorsFilter doesn't fail closed.
            allowCredentials = false;
        }

        // MUST be first: force UTF-8 decoding of every request body BEFORE any
        // downstream filter/servlet reads parameters or the stream. The servlet
        // default is ISO-8859-1, which mangles Tamil/Hindi/emoji into mojibake.
        addFilter(ctx, "0characterEncodingFilter", new CharacterEncodingFilter(), "/*");

        addFilter(ctx, "corsFilter", new CorsFilter(), "/*",
                "cors.allowed.origins", allowedOrigins,
                "cors.allowed.methods", "GET,POST,PUT,DELETE,PATCH,HEAD,OPTIONS",
                "cors.allowed.headers",
                "Content-Type,Authorization,X-Requested-With,Accept,Origin,Cache-Control",
                "cors.exposed.headers", "Authorization,Location",
                "cors.support.credentials", String.valueOf(allowCredentials),
                "cors.preflight.maxage", "1800");

        addFilter(ctx, "securityHeadersFilter", new SecurityHeadersFilter(), "/*",
                "security.hsts", envOrDefault("ENABLE_HSTS", "false"),
                "security.csp", envOrDefault("CONTENT_SECURITY_POLICY",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"));

        addFilter(ctx, "globalExceptionFilter", new GlobalExceptionFilter(), "/*");

        addFilter(ctx, "csrfGuardFilter", new CsrfGuardFilter(), "/*",
                "csrf.allowed.origins", allowedOrigins);

        addFilter(ctx, "authorizationFilter", new AuthorizationFilter(), "/*");
    }

    /**
     * Resolve the CORS / CSRF origin allowlist. Honours {@code CORS_ALLOWED_ORIGINS}
     * when set; otherwise derives a safe default from the configured app URLs
     * plus the usual local dev origins, instead of defaulting to {@code *}.
     */
    private static String corsAllowedOrigins() {
        String configured = System.getenv("CORS_ALLOWED_ORIGINS");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        java.util.LinkedHashSet<String> origins = new java.util.LinkedHashSet<>();
        addOrigin(origins, com.ecommerce.app.module.iam.config.ENVConfig.get("APP_HOME_URL"));
        addOrigin(origins, com.ecommerce.app.module.iam.config.ENVConfig.get("APP_BASE_URL"));
        origins.add("http://localhost:5173");
        origins.add("http://localhost:3000");
        return String.join(",", origins);
    }

    /** Reduce a full URL to its scheme://host[:port] origin and add it to the set. */
    private static void addOrigin(java.util.Set<String> set, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            if (u.getScheme() == null || u.getHost() == null) {
                return;
            }
            StringBuilder sb = new StringBuilder()
                    .append(u.getScheme()).append("://").append(u.getHost());
            if (u.getPort() != -1) {
                sb.append(':').append(u.getPort());
            }
            set.add(sb.toString());
        } catch (IllegalArgumentException ignored) {
            // Not a parseable URL -> skip.
        }
    }

    private static void registerServlets(Context ctx) {
        // Auth & identity
        mount(ctx, new AuthController(), "/auth");
        mount(ctx, new EmailVerificationController(), "/api/email-verify", "/api/email-verify/resend");
        mount(ctx, new PasswordResetController(), "/api/password-reset", "/api/password-reset/confirm");
        mount(ctx, new MeController(), "/api/me");
        mount(ctx, new UserContoller(), "/api/user");
        mount(ctx, new RoleController(), "/api/role");
        mount(ctx, new RoleMappingController(), "/map");
        mount(ctx, new PermissionContoller(), "/api/permission");

        // Catalog & shopping
        mount(ctx, new ProductController(), "/api/product");
        mount(ctx, new ProductSearchController(), "/api/product/search", "/api/product/search/*");
        mount(ctx, new ProductVariantController(), "/api/product/variant");
        mount(ctx, new ProductImageController(), "/api/product/image");
        mount(ctx, new CategoryController(), "/api/category");
        mount(ctx, new CartController(), "/api/cart");
        mount(ctx, new WishlistController(), "/api/wishlist");
        // Web Push: public vapid key + authed subscribe/unsubscribe
        mount(ctx, new PushController(), "/api/push", "/api/push/*");
        mount(ctx, new OrderController(), "/api/order");
        mount(ctx, new InvoiceController(), "/api/order/invoice");
        mount(ctx, new PaymentController(), "/api/payment");
        mount(ctx, new ReviewController(), "/api/review");
        mount(ctx, new UserAddressController(), "/api/address");
        mount(ctx, new ContactController(), "/api/contact");

        // Festival Banner System:
        //   public:  /api/banner/festivals, /api/banner/festivals/*/images, /api/banner/share/*
        //   admin:   /api/banner/admin/festivals, /api/banner/admin/images, /api/banner/admin/messages
        mount(ctx, new BannerController(),
                "/api/banner",
                "/api/banner/*");
        mount(ctx, new MailController(), "/api/mail");
        mount(ctx, new AdminController(), "/api/admin");

        // Shipping (Delhivery integration)
        mount(ctx, new ShippingController(), "/api/shipping");
        mount(ctx, new AdminShippingController(), "/api/admin/shipping");

        // Quick / hyperlocal delivery (Rapido integration)
        mount(ctx, new QuickDeliveryController(), "/api/quick");
        mount(ctx, new AdminQuickController(), "/api/admin/quick");

        // Loyalty card (QR check-in): customer scan + admin approval console
        mount(ctx, new LoyaltyController(), "/api/loyalty", "/api/loyalty/*");
        mount(ctx, new AdminLoyaltyController(), "/api/admin/loyalty");
        mount(ctx, new com.ecommerce.app.module.loyalty.LoyaltyRedemptionController(),
                "/api/loyalty/redeem", "/api/loyalty/redeem/*");
        mount(ctx, new com.ecommerce.app.module.loyalty.AdminLoyaltyRedemptionController(),
                "/api/admin/loyalty/redemptions", "/api/admin/loyalty/redemptions/*");

        // Affiliate / referral: public click-track + authed link mgmt; admin rate config
        mount(ctx, new AffiliateController(), "/api/affiliate", "/api/affiliate/*");
        mount(ctx, new AdminAffiliateController(), "/api/admin/affiliate/settings");
        mount(ctx, new RapidoWebhookController(), "/api/integrations/rapido/webhook");

        // Health probes (multi-pattern: exact and sub-paths)
        mount(ctx, new HealthController(), "/health", "/health/*");

        // Lightweight liveness ping for uptime monitors (UptimeRobot etc).
        // No DB call — keeps Render warm without waking Neon every cycle.
        mount(ctx, new PingController(), "/ping");

        // SEO: dynamic product sitemap consumed by Google Search Console.
        mount(ctx, new com.ecommerce.app.module.seo.SitemapServlet(), "/sitemap-products.xml");
    }

    // --- low-level Tomcat plumbing -----------------------------------------

    /**
     * Register a servlet using its <strong>fully-qualified class name</strong>
     * as the servlet name. This matters because {@code AuthorizationFilter}
     * looks the servlet class back up via
     * {@code Class.forName(request.getHttpServletMapping().getServletName())}
     * to read its {@code @RequiresRole} / {@code @RequiresPermission}
     * annotations. Using the FQCN keeps that lookup working without a
     * separate name->class registry.
     */
    private static void mount(Context ctx, HttpServlet servlet, String... urlPatterns) {
        String name = servlet.getClass().getName();
        org.apache.catalina.Wrapper wrapper = Tomcat.addServlet(ctx, name, servlet);

        // Embedded Tomcat does not auto-process @MultipartConfig on
        // programmatically-registered servlets, which silently breaks
        // multipart form uploads (request.getParameter() / getParts()
        // both come back empty). Honour the annotation by hand so
        // upload-capable endpoints (e.g. POST /api/product) work.
        jakarta.servlet.annotation.MultipartConfig mpc =
                servlet.getClass().getAnnotation(jakarta.servlet.annotation.MultipartConfig.class);
        if (mpc != null) {
            wrapper.setMultipartConfigElement(new jakarta.servlet.MultipartConfigElement(
                    mpc.location(),
                    mpc.maxFileSize(),
                    mpc.maxRequestSize(),
                    mpc.fileSizeThreshold()));
        }

        for (String p : urlPatterns) {
            ctx.addServletMappingDecoded(p, name);
        }
    }

    private static void addFilter(Context ctx, String name, Filter filter, String urlPattern,
            String... initParams) {
        FilterDef def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        for (int i = 0; i + 1 < initParams.length; i += 2) {
            def.addInitParameter(initParams[i], initParams[i + 1]);
        }
        ctx.addFilterDef(def);

        FilterMap map = new FilterMap();
        map.setFilterName(name);
        map.addURLPattern(urlPattern);
        ctx.addFilterMap(map);
    }

    private static int parsePort(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
