package com.ecommerce.app.module.delivery;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Rapido hyperlocal delivery provider adapter.
 *
 * Follows the same singleton + env-driven pattern as {@code RazorpayClient}
 * and {@code DelhiveryClient}.
 *
 * Environment variables (loaded by ENVConfig):
 *   RAPIDO_API_KEY          - API key / token from Rapido partner dashboard
 *   RAPIDO_BASE_URL         - e.g. https://api.rapido.bike (prod) / sandbox URL
 *   RAPIDO_WEBHOOK_SECRET   - HMAC secret for verifying inbound status webhooks
 *
 * NOTE: Rapido's exact partner-API contract varies by onboarding. The request
 * payloads and JSON paths below use the common field names; adjust the marked
 * paths to match your signed partner spec. The adapter shape stays stable so
 * the rest of the backend never changes.
 */
public final class RapidoClient implements DeliveryProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RapidoClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile RapidoClient INSTANCE;

    private final String apiKey;
    private final String baseUrl;
    private final String webhookSecret;

    private RapidoClient() {
        this.apiKey = ENVConfig.get("RAPIDO_API_KEY");
        this.baseUrl = ENVConfig.get("RAPIDO_BASE_URL") != null
                ? ENVConfig.get("RAPIDO_BASE_URL")
                : "https://api.rapido.bike";
        this.webhookSecret = ENVConfig.get("RAPIDO_WEBHOOK_SECRET");
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("RAPIDO_API_KEY not set. Quick-delivery quoting/booking will fail until configured.");
        }
    }

    public static RapidoClient get() {
        RapidoClient local = INSTANCE;
        if (local == null) {
            synchronized (RapidoClient.class) {
                if (INSTANCE == null) INSTANCE = new RapidoClient();
                local = INSTANCE;
            }
        }
        return local;
    }

    @Override public String code() { return "RAPIDO"; }

    @Override public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }

    // ------------------------------------------------------------------
    // Quote
    // ------------------------------------------------------------------
    @Override
    public QuoteResult quote(QuoteInput in) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Rapido API key not configured");
        }
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode pickup = body.putObject("pickup");
        pickup.put("lat", in.pickupLat());
        pickup.put("lng", in.pickupLng());
        ObjectNode drop = body.putObject("drop");
        drop.put("lat", in.dropLat());
        drop.put("lng", in.dropLng());
        body.put("weight", in.weightGrams());

        JsonNode root = send("POST", "/v1/quote", MAPPER.writeValueAsString(body));

        // --- adjust these JSON paths to your signed Rapido spec ---
        boolean serviceable = root.path("serviceable").asBoolean(true);
        boolean rider = root.path("rider_available").asBoolean(true);
        double fee = root.path("fare").path("total").asDouble(
                root.path("estimated_fare").asDouble(0));
        int eta = root.path("eta_minutes").asInt(root.path("eta").asInt(0));
        double dist = root.path("distance_km").asDouble(0);
        String quoteId = root.path("quote_id").asText(null);

        return new QuoteResult(serviceable, rider, fee, eta, dist, quoteId,
                root.path("message").asText(null));
    }

    @Override
    public boolean riderAvailable(QuoteInput in) throws Exception {
        // Reuse the quote endpoint — it reports live rider availability.
        return quote(in).riderAvailable();
    }

    // ------------------------------------------------------------------
    // Book
    // ------------------------------------------------------------------
    @Override
    public BookingResult book(BookingInput in) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Rapido API key not configured");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("reference_id", in.orderId());
        if (in.providerQuoteId() != null) body.put("quote_id", in.providerQuoteId());
        body.put("order_value", in.orderValue());
        body.put("weight", in.weightGrams());

        ObjectNode pickup = body.putObject("pickup");
        pickup.put("lat", in.pickupLat());
        pickup.put("lng", in.pickupLng());
        pickup.put("address", in.pickupAddress());
        pickup.put("contact_name", in.pickupContactName());
        pickup.put("contact_phone", in.pickupContactPhone());

        ObjectNode drop = body.putObject("drop");
        drop.put("lat", in.dropLat());
        drop.put("lng", in.dropLng());
        drop.put("address", in.dropAddress());
        drop.put("contact_name", in.dropContactName());
        drop.put("contact_phone", in.dropContactPhone());

        JsonNode root = send("POST", "/v1/orders", MAPPER.writeValueAsString(body));

        boolean ok = root.path("success").asBoolean(true);
        String providerOrderId = root.path("order_id").asText(root.path("id").asText(null));
        String status = root.path("status").asText("CREATED");
        double fee = root.path("fare").path("total").asDouble(root.path("fare").asDouble(0));
        int eta = root.path("eta_minutes").asInt(0);
        String riderName = root.path("rider").path("name").asText(null);
        String riderPhone = root.path("rider").path("phone").asText(null);
        String trackingUrl = root.path("tracking_url").asText(null);

        if (!ok || providerOrderId == null || providerOrderId.isBlank()) {
            return new BookingResult(false, null, "FAILED", 0, 0, null, null, null,
                    root.toString(), root.path("message").asText("Rapido rejected the booking"));
        }
        return new BookingResult(true, providerOrderId, status, fee, eta,
                riderName, riderPhone, trackingUrl, root.toString(), null);
    }

    // ------------------------------------------------------------------
    // Cancel
    // ------------------------------------------------------------------
    @Override
    public boolean cancel(String providerOrderId) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Rapido API key not configured");
        JsonNode root = send("POST", "/v1/orders/" + providerOrderId + "/cancel", "{}");
        return root.path("success").asBoolean(true);
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------
    @Override
    public StatusResult status(String providerOrderId) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Rapido API key not configured");
        JsonNode root = send("GET", "/v1/orders/" + providerOrderId, null);
        return new StatusResult(
                root.path("status").asText("UNKNOWN"),
                root.path("rider").path("name").asText(null),
                root.path("rider").path("phone").asText(null),
                root.path("tracking_url").asText(null),
                root.toString());
    }

    // ------------------------------------------------------------------
    // Webhook signature verification (status updates Rapido -> us)
    // ------------------------------------------------------------------
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (rawBody == null || signature == null) return false;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOG.warn("RAPIDO_WEBHOOK_SECRET not set; rejecting webhook");
            return false;
        }
        return constantTimeEquals(hmacSha256Hex(webhookSecret, rawBody), signature);
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------
    private JsonNode send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json");

        if ("GET".equals(method)) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
        }

        HttpRequest req = b.build();
        LOG.info("[RAPIDO] {} {}", method, path);
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        LOG.info("[RAPIDO] <- HTTP {} {}", res.statusCode(), truncate(res.body()));
        if (res.statusCode() / 100 != 2) {
            throw new RuntimeException("Rapido API " + path + " returned " + res.statusCode() + ": " + res.body());
        }
        return MAPPER.readTree(res.body());
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte x : raw) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int r = 0;
        for (int i = 0; i < x.length; i++) r |= x[i] ^ y[i];
        return r == 0;
    }
}
