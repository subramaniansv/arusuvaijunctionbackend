package com.ecommerce.app.module.push;

import com.ecommerce.app.module.iam.config.ENVConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends Web Push notifications to every device a user has subscribed.
 *
 * Config (env vars via ENVConfig):
 *   VAPID_PUBLIC_KEY   base64url, 65-byte EC public key (also given to the browser)
 *   VAPID_PRIVATE_KEY  base64url, 32-byte raw scalar
 *   VAPID_SUBJECT      "mailto:you@domain.com" (contact for the push service)
 *
 * Notifications are fire-and-forget on a small thread pool so request-handling
 * threads (order placement, etc.) never block on the push service.
 */
public final class PushService {

    private static final Logger LOG = LoggerFactory.getLogger(PushService.class);

    private static final PushService INSTANCE = new PushService();
    public static PushService get() { return INSTANCE; }

    private final PushRepository repo = new PushRepository();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "webpush-sender");
        t.setDaemon(true);
        return t;
    });

    private final String publicKey  = ENVConfig.get("VAPID_PUBLIC_KEY");
    private final String privateKey = ENVConfig.get("VAPID_PRIVATE_KEY");
    private final String subject    = orDefault(ENVConfig.get("VAPID_SUBJECT"), "mailto:admin@arusuvaijunction.com");

    public boolean isConfigured() {
        return publicKey != null && privateKey != null;
    }

    public String getPublicKey() { return publicKey; }

    /**
     * Send one notification to all of a user's devices. The payload is the JSON
     * the service worker will read in its 'push' event:
     *   { "title": "...", "body": "...", "url": "/orders/123", "icon": "/favicon.png" }
     */
    public void sendToUser(UUID userId, String title, String body, String url) {
        if (!isConfigured()) {
            LOG.warn("web-push not configured (VAPID keys missing) — skipping notification");
            return;
        }
        String json = toJson(title, body, url);
        pool.submit(() -> {
            List<PushSubscription> subs = repo.findByUser(userId);
            for (PushSubscription s : subs) {
                try {
                    deliver(s, json.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOG.warn("push deliver failed for endpoint {}: {}",
                            abbreviate(s.getEndpoint()), e.getMessage());
                }
            }
        });
    }

    /**
     * Send one notification to every device owned by an Admin-role user.
     * Used for staff alerts: new paid order, pending check-in approval, pending
     * points-usage (offline redemption) approval. Fire-and-forget; never blocks
     * the caller and never throws.
     */
    public void sendToAdmins(String title, String body, String url) {
        if (!isConfigured()) {
            LOG.warn("web-push not configured (VAPID keys missing) — skipping admin notification");
            return;
        }
        String json = toJson(title, body, url);
        pool.submit(() -> {
            List<PushSubscription> subs = repo.findAdminSubscriptions();
            for (PushSubscription s : subs) {
                try {
                    deliver(s, json.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    LOG.warn("admin push deliver failed for endpoint {}: {}",
                            abbreviate(s.getEndpoint()), e.getMessage());
                }
            }
        });
    }

    private void deliver(PushSubscription s, byte[] payload) throws Exception {
        WebPushCrypto.Encrypted enc = WebPushCrypto.encrypt(s.getP256dh(), s.getAuth(), payload);

        String audience = originOf(s.getEndpoint());
        String vapid = WebPushCrypto.vapidAuthHeader(audience, subject, publicKey, privateKey);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(s.getEndpoint()))
                .timeout(Duration.ofSeconds(15))
                .header("TTL", "2419200")                 // 28 days: deliver when device comes online
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Urgency", "high")
                .header("Authorization", vapid)
                .POST(HttpRequest.BodyPublishers.ofByteArray(enc.body))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code == 201 || code == 200 || code == 202) {
            return; // accepted / queued
        }
        if (code == 404 || code == 410) {
            // Subscription is gone — the browser unsubscribed or was uninstalled.
            LOG.info("push endpoint dead ({}), pruning", code);
            repo.deleteByEndpoint(s.getEndpoint());
            return;
        }
        LOG.warn("push service returned {} for {}: {}", code, abbreviate(s.getEndpoint()), res.body());
    }

    private static String originOf(String endpoint) {
        URI u = URI.create(endpoint);
        return u.getScheme() + "://" + u.getHost();
    }

    private static String toJson(String title, String body, String url) {
        return "{\"title\":" + q(title) + ",\"body\":" + q(body)
                + ",\"url\":" + q(url == null ? "/" : url)
                + ",\"icon\":\"/favicon.png\",\"badge\":\"/favicon.png\"}";
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 48 ? s.substring(0, 48) + "…" : s);
    }

    private static String orDefault(String v, String d) {
        return (v == null || v.isBlank()) ? d : v;
    }
}
