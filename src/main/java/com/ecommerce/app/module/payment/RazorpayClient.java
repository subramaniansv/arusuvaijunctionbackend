package com.ecommerce.app.module.payment;

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
import java.util.Base64;

/**
 * Thin wrapper over Razorpay's HTTP API.
 *
 * Credentials come from environment variables (loaded by ENVConfig):
 *   RAZORPAY_KEY_ID         - public key id, sent to frontend, used in HTTP Basic auth
 *   RAZORPAY_KEY_SECRET     - private key, server-only, used in HTTP Basic auth + HMAC payment-signature verify
 *   RAZORPAY_WEBHOOK_SECRET - shared secret configured in the Razorpay dashboard webhook, used for HMAC webhook verify
 *
 * The class is intentionally minimal: a single dependency (java.net.http) and Jackson.
 * It exposes the bits the rest of the codebase actually needs and nothing else.
 */
public class RazorpayClient {
    private static final Logger LOG = LoggerFactory.getLogger(RazorpayClient.class);
    private static final String API_ORDERS = "https://api.razorpay.com/v1/orders";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile RazorpayClient INSTANCE;

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    private RazorpayClient() {
        this.keyId = ENVConfig.get("RAZORPAY_KEY_ID");
        this.keySecret = ENVConfig.get("RAZORPAY_KEY_SECRET");
        this.webhookSecret = ENVConfig.get("RAZORPAY_WEBHOOK_SECRET");
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            LOG.warn("RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET not set. Payments will fail until configured in .env");
        }
    }

    public static RazorpayClient get() {
        RazorpayClient local = INSTANCE;
        if (local == null) {
            synchronized (RazorpayClient.class) {
                if (INSTANCE == null) INSTANCE = new RazorpayClient();
                local = INSTANCE;
            }
        }
        return local;
    }

    public String getKeyId() {
        return keyId;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    /**
     * Create a Razorpay order.
     *
     * @param amountInPaise total amount, in the smallest currency unit (INR -> paise). Razorpay rejects floating-point amounts.
     * @param currency      ISO 4217, e.g. "INR"
     * @param receipt       our internal order id (string). Free-form, surfaces in the Razorpay dashboard.
     * @return Razorpay's response order_id (e.g. "order_NXyz...")
     */
    public String createOrder(long amountInPaise, String currency, String receipt) {
        if (!isConfigured()) {
            throw new RuntimeException("payment gateway is not configured");
        }
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("amount", amountInPaise);
            body.put("currency", currency == null ? "INR" : currency);
            if (receipt != null) body.put("receipt", receipt);
            body.put("payment_capture", 1); // auto-capture; otherwise we'd need a separate capture call after authorize

            String creds = keyId + ":" + keySecret;
            String basic = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_ORDERS))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                LOG.error("razorpay createOrder failed status={} body={}", resp.statusCode(), resp.body());
                throw new RuntimeException("payment gateway rejected order");
            }
            JsonNode node = MAPPER.readTree(resp.body());
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) {
                LOG.error("razorpay createOrder missing id in response body={}", resp.body());
                throw new RuntimeException("payment gateway returned invalid response");
            }
            return id;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("razorpay createOrder error", e);
            throw new RuntimeException("could not reach payment gateway");
        }
    }

    /**
     * Verify the signature returned by Razorpay checkout (handler callback).
     * payload = razorpay_order_id + "|" + razorpay_payment_id, signed with the key secret.
     */
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String signature) {
        if (razorpayOrderId == null || razorpayPaymentId == null || signature == null) return false;
        if (keySecret == null || keySecret.isBlank()) return false;
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        return constantTimeEquals(hmacSha256Hex(keySecret, payload), signature);
    }

    /**
     * Verify a Razorpay webhook signature. Razorpay sends the signature in the
     * `X-Razorpay-Signature` header; the payload is the raw request body BYTES
     * (do not re-serialize the JSON, the whitespace must match exactly).
     */
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (rawBody == null || signature == null) return false;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            LOG.warn("RAZORPAY_WEBHOOK_SECRET not set; rejecting webhook");
            return false;
        }
        return constantTimeEquals(hmacSha256Hex(webhookSecret, rawBody), signature);
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /** Constant-time string compare to avoid timing oracles on signature checks. */
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
