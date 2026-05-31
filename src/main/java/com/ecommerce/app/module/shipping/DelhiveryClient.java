package com.ecommerce.app.module.shipping;

import com.ecommerce.app.module.iam.config.ENVConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper over Delhivery's HTTP API.
 *
 * Credentials come from environment variables (loaded by ENVConfig):
 *   DELHIVERY_API_TOKEN    - API token from Delhivery dashboard
 *   DELHIVERY_BASE_URL     - https://track.delhivery.com (prod) or https://staging-express.delhivery.com (sandbox)
 *   DELHIVERY_ORIGIN_PIN   - warehouse origin pincode (e.g. 627006)
 *   DELHIVERY_PICKUP_NAME  - pickup location name registered in Delhivery dashboard
 *
 * Follows the same singleton pattern as RazorpayClient.
 */
public class DelhiveryClient {
    private static final Logger LOG = LoggerFactory.getLogger(DelhiveryClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static volatile DelhiveryClient INSTANCE;

    private final String token;
    private final String baseUrl;
    private final String originPin;
    private final String pickupName;

    private DelhiveryClient() {
        this.token = ENVConfig.get("DELHIVERY_API_TOKEN");
        this.baseUrl = ENVConfig.get("DELHIVERY_BASE_URL") != null
                ? ENVConfig.get("DELHIVERY_BASE_URL")
                : "https://track.delhivery.com";
        this.originPin = ENVConfig.get("DELHIVERY_ORIGIN_PIN") != null
                ? ENVConfig.get("DELHIVERY_ORIGIN_PIN")
                : "627006";
        this.pickupName = ENVConfig.get("DELHIVERY_PICKUP_NAME") != null
                ? ENVConfig.get("DELHIVERY_PICKUP_NAME")
                : "default";

        LOG.info("[DELHIVERY-INIT] token={} baseUrl={} originPin={} pickupName={}",
                token != null ? token.substring(0, Math.min(6, token.length())) + "..." : "NULL",
                baseUrl, originPin, pickupName);

        if (token == null || token.isBlank()) {
            LOG.warn("DELHIVERY_API_TOKEN not set. Shipping API calls will fail until configured.");
        }
    }

    public static DelhiveryClient get() {
        DelhiveryClient local = INSTANCE;
        if (local == null) {
            synchronized (DelhiveryClient.class) {
                if (INSTANCE == null) INSTANCE = new DelhiveryClient();
                local = INSTANCE;
            }
        }
        return local;
    }

    public String getToken() { return token; }
    public String getBaseUrl() { return baseUrl; }
    public String getOriginPin() { return originPin; }
    public String getPickupName() { return pickupName; }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    // ------------------------------------------------------------------
    // 1. Pincode Serviceability Check
    // GET /c/api/pin-codes/json/?filter_codes={pin}
    // ------------------------------------------------------------------

    public ServiceabilityResult checkServiceability(String destinationPin) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Delhivery API token not configured");
        }

        String url = baseUrl + "/c/api/pin-codes/json/?filter_codes=" + destinationPin;
        LOG.info("[DELHIVERY-CHECK] GET {}", url);
        LOG.info("[DELHIVERY-CHECK] Auth header: Token {}...", token.substring(0, Math.min(6, token.length())));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        LOG.info("[DELHIVERY-CHECK] Response HTTP {} body={}", res.statusCode(), res.body());
        if (res.statusCode() != 200) {
            LOG.error("[DELHIVERY-CHECK] FAILED: HTTP {} body={}", res.statusCode(), res.body());
            throw new RuntimeException("Delhivery API returned " + res.statusCode());
        }

        JsonNode root = MAPPER.readTree(res.body());
        JsonNode deliveryCodes = root.get("delivery_codes");

        if (deliveryCodes == null || !deliveryCodes.isArray() || deliveryCodes.isEmpty()) {
            return new ServiceabilityResult(false, false, 0, null);
        }

        // Check first entry
        JsonNode postal = deliveryCodes.get(0).path("postal_code");
        boolean prepaid = "Y".equalsIgnoreCase(postal.path("pre_paid").asText());
        boolean cod = "Y".equalsIgnoreCase(postal.path("cod").asText());
        int estimatedDays = postal.path("max_days").asInt(7);
        String district = postal.path("district").asText(null);

        return new ServiceabilityResult(true, cod, estimatedDays, district);
    }

    // ------------------------------------------------------------------
    // 2. Rate Calculation
    // GET /api/kinko/v1/invoice/charges/.json?md=E&ss=Delivered&d_pin=...&o_pin=...&cgm=...&pt=Pre-paid&cod=0
    // ------------------------------------------------------------------

    public RateResult calculateRate(String destinationPin, int weightGrams) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Delhivery API token not configured");
        }

        String url = baseUrl + "/api/kinko/v1/invoice/charges/.json"
                + "?md=E"
                + "&ss=Delivered"
                + "&d_pin=" + destinationPin
                + "&o_pin=" + originPin
                + "&cgm=" + weightGrams
                + "&pt=Pre-paid"
                + "&cod=0";

        LOG.info("[DELHIVERY-RATE] GET {}", url);
        LOG.info("[DELHIVERY-RATE] Auth header: Token {}...", token.substring(0, Math.min(6, token.length())));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        LOG.info("[DELHIVERY-RATE] Response HTTP {} body={}", res.statusCode(), res.body());
        if (res.statusCode() != 200) {
            LOG.error("[DELHIVERY-RATE] FAILED: HTTP {} body={}", res.statusCode(), res.body());
            throw new RuntimeException("Delhivery rate API returned " + res.statusCode());
        }

        JsonNode root = MAPPER.readTree(res.body());
        // Response is an array; first element has the charges
        JsonNode charges = root.isArray() ? root.get(0) : root;
        double totalAmount = charges.path("total_amount").asDouble(0);
        String zone = charges.path("zone").asText("unknown");
        double chargedWeight = charges.path("charged_weight").asDouble(weightGrams);

        return new RateResult(totalAmount, zone, chargedWeight);
    }

    // ------------------------------------------------------------------
    // 3. Create Shipment (Waybill generation)
    // POST /api/cmu/create.json
    // ------------------------------------------------------------------

    public ShipmentResult createShipment(ShipmentRequest shipmentReq) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Delhivery API token not configured");
        }

        // Build the shipment JSON payload
        ObjectNode shipment = MAPPER.createObjectNode();
        shipment.put("name", shipmentReq.customerName());
        shipment.put("add", shipmentReq.address());
        shipment.put("pin", shipmentReq.pincode());
        shipment.put("city", shipmentReq.city());
        shipment.put("state", shipmentReq.state());
        shipment.put("country", "India");
        shipment.put("phone", shipmentReq.phone());
        shipment.put("order", shipmentReq.orderId());
        shipment.put("payment_mode", "Pre-paid");
        shipment.put("products_desc", "Food items");
        shipment.put("total_amount", shipmentReq.totalAmount());
        shipment.put("cod_amount", "0");
        shipment.put("weight", shipmentReq.weightGrams());
        shipment.put("shipment_width", shipmentReq.widthCm() > 0 ? shipmentReq.widthCm() : 20);
        shipment.put("shipment_height", shipmentReq.heightCm() > 0 ? shipmentReq.heightCm() : 15);
        shipment.put("shipment_length", shipmentReq.lengthCm() > 0 ? shipmentReq.lengthCm() : 25);
        shipment.put("return_pin", originPin);
        shipment.put("return_city", "Tirunelveli");
        shipment.put("return_state", "Tamil Nadu");
        shipment.put("return_country", "India");

        ObjectNode pickup = MAPPER.createObjectNode();
        pickup.put("name", pickupName);

        ObjectNode body = MAPPER.createObjectNode();
        body.set("shipments", MAPPER.createArrayNode().add(shipment));
        body.set("pickup_location", pickup);

        String jsonBody = MAPPER.writeValueAsString(body);
        LOG.info("[DELHIVERY] createShipment payload: {}", jsonBody);

        // Delhivery expects format=json parameter and the data as form-encoded "data" field
        // OR direct JSON body depending on endpoint version. Using JSON body approach:
        String formBody = "format=json&data=" + URLEncoder.encode(jsonBody, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/cmu/create.json"))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        LOG.info("[DELHIVERY] createShipment response: HTTP {} body={}", res.statusCode(), res.body());
        if (res.statusCode() != 200) {
            LOG.error("Delhivery create shipment failed: HTTP {} body={}", res.statusCode(), res.body());
            throw new RuntimeException("Delhivery shipment creation failed: " + res.statusCode());
        }

        JsonNode root = MAPPER.readTree(res.body());
        boolean success = root.path("success").asBoolean(false);
        JsonNode packages = root.path("packages");

        if (!success || !packages.isArray() || packages.isEmpty()) {
            String remark = root.path("rmk").asText(root.toString());
            LOG.error("Delhivery shipment rejected: {}", remark);
            throw new RuntimeException("Shipment rejected by Delhivery: " + remark);
        }

        JsonNode pkg = packages.get(0);
        String waybill = pkg.path("waybill").asText(null);
        String status = pkg.path("status").asText("Unknown");

        if (waybill == null || waybill.isBlank()) {
            throw new RuntimeException("Delhivery did not return a waybill");
        }

        return new ShipmentResult(waybill, status, true);
    }

    // ------------------------------------------------------------------
    // 4. Tracking
    // GET /api/v1/packages/json/?waybill={awb}
    // ------------------------------------------------------------------

    public TrackingResult track(String waybill) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Delhivery API token not configured");
        }

        String url = baseUrl + "/api/v1/packages/json/?waybill=" + waybill;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Token " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            LOG.error("Delhivery tracking failed: HTTP {}", res.statusCode());
            throw new RuntimeException("Delhivery tracking API returned " + res.statusCode());
        }

        JsonNode root = MAPPER.readTree(res.body());
        JsonNode shipmentData = root.path("ShipmentData");
        if (!shipmentData.isArray() || shipmentData.isEmpty()) {
            return new TrackingResult(waybill, "NOT_FOUND", null, null, List.of());
        }

        JsonNode shipment = shipmentData.get(0).path("Shipment");
        String status = shipment.path("Status").path("Status").asText("Unknown");
        String statusDate = shipment.path("Status").path("StatusDateTime").asText(null);
        String expectedDate = shipment.path("ExpectedDeliveryDate").asText(null);

        List<TrackingScan> scans = new ArrayList<>();
        JsonNode scanDetail = shipment.path("Scans");
        if (scanDetail.isArray()) {
            for (JsonNode scanNode : scanDetail) {
                JsonNode s = scanNode.path("ScanDetail");
                scans.add(new TrackingScan(
                        s.path("Scan").asText(),
                        s.path("ScanDateTime").asText(),
                        s.path("ScannedLocation").asText(),
                        s.path("Instructions").asText("")
                ));
            }
        }

        return new TrackingResult(waybill, status, statusDate, expectedDate, scans);
    }

    // ------------------------------------------------------------------
    // 5. Cancel shipment
    // POST /api/p/edit  (form: waybill=AWB&cancellation=true)
    // ------------------------------------------------------------------

    public boolean cancelShipment(String waybill) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Delhivery API token not configured");
        }

        String formBody = "waybill=" + URLEncoder.encode(waybill, StandardCharsets.UTF_8)
                + "&cancellation=true";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/p/edit"))
                .header("Authorization", "Token " + token)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        LOG.info("[DELHIVERY-CANCEL] POST {} waybill={}", baseUrl + "/api/p/edit", waybill);
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        LOG.info("[DELHIVERY-CANCEL] Response HTTP {} body={}", res.statusCode(), res.body());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Delhivery cancel failed: HTTP " + res.statusCode() + " " + res.body());
        }
        return true;
    }
}
