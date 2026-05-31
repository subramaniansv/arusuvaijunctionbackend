package com.ecommerce.app.module.shipping;

import com.ecommerce.app.config.DBConfig;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.ecommerce.app.module.order.Order;
import com.ecommerce.app.module.order.OrderRepository;
import com.ecommerce.app.module.order.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin-only shipping endpoints for creating Delhivery shipments and tracking.
 *
 * POST /api/admin/shipping?action=ship
 *   body: { "orderId": "...", "weightGrams": 500 (optional override) }
 *   -> creates shipment in Delhivery, saves AWB to order, sets status to SHIPPED
 *
 * GET /api/admin/shipping?action=track&orderId=...
 *   -> returns full tracking details from Delhivery
 */
@WebServlet("/api/admin/shipping")
@RequiresRole("Admin")
public class AdminShippingController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminShippingController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PINCODE_RE = Pattern.compile("\\b(\\d{6})\\b");

    private final ShippingService shippingService = new ShippingService();
    private final OrderRepository orderRepository = new OrderRepository();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("ship".equals(action)) {
            try {
                handleShip(request, response);
            } catch (Exception e) {
                LOG.error("Admin ship error", e);
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "Failed to create shipment: " + e.getMessage(), null, 500), response);
            }
        } else if ("cancel".equals(action)) {
            try {
                handleCancel(request, response);
            } catch (Exception e) {
                LOG.error("Admin cancel shipment error", e);
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "Failed to cancel shipment: " + e.getMessage(), null, 500), response);
            }
        } else {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Use action=ship or action=cancel", null, 400), response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (!"track".equals(action)) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Use action=track", null, 400), response);
            return;
        }

        try {
            handleTrack(request, response);
        } catch (Exception e) {
            LOG.error("Admin track error", e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Tracking failed: " + e.getMessage(), null, 500), response);
        }
    }

    private void handleShip(HttpServletRequest request, HttpServletResponse response) throws Exception {
        JsonNode body = MAPPER.readTree(request.getInputStream());
        String orderIdStr = body.path("orderId").asText(null);
        if (orderIdStr == null || orderIdStr.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "orderId is required", null, 400), response);
            return;
        }

        UUID orderId = UUID.fromString(orderIdStr);
        int weightOverride = body.path("weightGrams").asInt(0);

        LOG.info("[SHIP] === Creating Delhivery shipment for order {} ===", orderId);
        LOG.info("[SHIP] Weight override from frontend: {}g", weightOverride);

        // Fetch the order
        Order order;
        try (Connection conn = DBConfig.getConnection()) {
            order = findOrderById(conn, orderId);
        }

        if (order == null) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order not found", null, 404), response);
            return;
        }

        if (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order already has AWB: " + order.getTrackingNumber(), null, 409), response);
            return;
        }

        // Fetch order items for logging & weight calculation
        List<Map<String, Object>> orderItems = fetchOrderItems(orderId);
        LOG.info("[SHIP] Order {} | status={} | total=₹{} | items:", orderId, order.getStatus(), order.getTotalAmount());
        int calculatedWeight = 0;
        for (Map<String, Object> item : orderItems) {
            String productName = (String) item.get("productName");
            String variantLabel = (String) item.get("variantLabel");
            int qty = (int) item.get("quantity");
            int itemWeight = variantWeightGrams(variantLabel) * qty;
            calculatedWeight += itemWeight;
            LOG.info("[SHIP]   - {} (variant: {}) × {} = {}g", productName, variantLabel, qty, itemWeight);
        }
        if (calculatedWeight == 0) {
            LOG.warn("[SHIP] Cannot calculate weight - order items missing variant labels");
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Cannot ship: order items are missing variant labels. Please ensure all products have variants with weight labels.", null, 400), response);
            return;
        }
        LOG.info("[SHIP] Calculated total weight: {}g", calculatedWeight);

        // Parse address parts for Delhivery
        String address = order.getShippingAddress();
        String pincode = extractPincode(address);
        if (pincode == null) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Cannot extract pincode from shipping address", null, 400), response);
            return;
        }

        // Parse city/state from address (format: "Name\nLine1\nLine2\nCity, State, Pin\nIndia")
        String[] lines = address.split("\\n");
        String customerName = lines.length > 0 ? lines[0].trim() : "Customer";
        String streetAddress = address; // fallback: full address
        String city = "Unknown";
        String state = "Unknown";

        // Expected format: Name \n Street \n City, State, Pin \n Country
        // or: Name \n Street1 \n Street2 \n City, State, Pin \n Country
        if (lines.length >= 3) {
            // Last line is typically "India", second-to-last has "City, State, Pin" or "City, State"
            // Street is everything between name (first line) and the city/state line
            int cityLineIdx = -1;
            for (int i = lines.length - 1; i >= 1; i--) {
                String l = lines[i].trim();
                if (l.equalsIgnoreCase("India") || l.equalsIgnoreCase("IN")) continue;
                if (l.contains(",")) {
                    cityLineIdx = i;
                    String[] parts = l.split(",");
                    city = parts[0].trim();
                    if (parts.length >= 2) state = parts[1].trim();
                    // Remove pincode from state if present
                    state = state.replaceAll("\\d{6}", "").trim();
                    if (state.isEmpty() && parts.length >= 3) state = parts[2].trim().replaceAll("\\d{6}", "").trim();
                    break;
                }
            }

            // Street = lines between name and city line
            StringBuilder sb = new StringBuilder();
            int endIdx = cityLineIdx > 0 ? cityLineIdx : lines.length - 1;
            for (int i = 1; i < endIdx; i++) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(lines[i].trim());
            }
            if (sb.length() > 0) streetAddress = sb.toString();
        }

        if (city.equals("Unknown") || state.equals("Unknown")) {
            // Fallback: try to guess from pincode area
            city = "Tirunelveli";
            state = "Tamil Nadu";
        }

        // Use frontend override if provided, otherwise use server-calculated weight
        int weight = weightOverride > 0 ? weightOverride : calculatedWeight;
        LOG.info("[SHIP] Final weight for Delhivery: {}g (source: {})", weight,
                weightOverride > 0 ? "frontend override" : "server calculated");
        LOG.info("[SHIP] Destination: {} pin={} city={} state={} street={}", customerName, pincode, city, state, streetAddress);

        ShipmentRequest shipReq = new ShipmentRequest(
                customerName,
                streetAddress,
                pincode,
                city,
                state,
                order.getPhone() != null ? order.getPhone() : "9894014063",
                orderId.toString(),
                order.getTotalAmount(),
                weight,
                0, 0, 0 // default dimensions
        );

        ShipmentResult result = shippingService.createShipment(shipReq);
        LOG.info("[SHIP] SUCCESS! AWB={} status={}", result.waybill(), result.status());

        // Save AWB and update status
        try (Connection conn = DBConfig.getConnection()) {
            String sql = "UPDATE orders SET tracking_number = ?, shipping_provider = ?, order_status = ? WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, result.waybill());
                ps.setString(2, "DELHIVERY");
                ps.setString(3, OrderStatus.SHIPPED.name());
                ps.setObject(4, orderId);
                ps.executeUpdate();
            }
        }

        SendResponseUtil.sendResponse(
                new ApiResponse(true, "Shipment created", Map.of(
                        "waybill", result.waybill(),
                        "status", result.status(),
                        "trackingUrl", "https://www.delhivery.com/track/package/" + result.waybill()
                ), 200), response);
    }

    private void handleTrack(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String orderIdStr = request.getParameter("orderId");
        if (orderIdStr == null || orderIdStr.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "orderId is required", null, 400), response);
            return;
        }

        UUID orderId = UUID.fromString(orderIdStr);
        Order order;
        try (Connection conn = DBConfig.getConnection()) {
            order = findOrderById(conn, orderId);
        }

        if (order == null) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order not found", null, 404), response);
            return;
        }

        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order has no tracking number yet", null, 404), response);
            return;
        }

        TrackingResult result = shippingService.trackShipment(order.getTrackingNumber());
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "ok", Map.of(
                        "tracking", result,
                        "trackingUrl", "https://www.delhivery.com/track/package/" + order.getTrackingNumber()
                ), 200), response);
    }

    private void handleCancel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        JsonNode body = MAPPER.readTree(request.getInputStream());
        String orderIdStr = body.path("orderId").asText(null);
        if (orderIdStr == null || orderIdStr.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "orderId is required", null, 400), response);
            return;
        }

        UUID orderId = UUID.fromString(orderIdStr);
        Order order;
        try (Connection conn = DBConfig.getConnection()) {
            order = findOrderById(conn, orderId);
        }

        if (order == null) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order not found", null, 404), response);
            return;
        }

        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Order has no shipment to cancel", null, 400), response);
            return;
        }

        // Cancel in Delhivery
        shippingService.cancelShipment(order.getTrackingNumber());

        // Clear AWB from order and revert status to CONFIRMED
        try (Connection conn = DBConfig.getConnection()) {
            String sql = "UPDATE orders SET tracking_number = NULL, shipping_provider = NULL, order_status = ? WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, OrderStatus.CONFIRMED.name());
                ps.setObject(2, orderId);
                ps.executeUpdate();
            }
        }

        SendResponseUtil.sendResponse(
                new ApiResponse(true, "Shipment cancelled, order reverted to CONFIRMED", null, 200), response);
    }

    private Order findOrderById(Connection conn, UUID orderId) throws Exception {
        String sql = "SELECT order_id, user_id, order_status, total_amount, shipping_fee, shipping_address, phone_number, tracking_number, shipping_provider FROM orders WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Order o = new Order();
                o.setOrderId(UUID.fromString(rs.getString("order_id")));
                o.setUserId(UUID.fromString(rs.getString("user_id")));
                o.setStatus(OrderStatus.valueOf(rs.getString("order_status")));
                o.setTotalAmount(rs.getDouble("total_amount"));
                o.setShippingFee(rs.getDouble("shipping_fee"));
                o.setShippingAddress(rs.getString("shipping_address"));
                o.setPhone(rs.getString("phone_number"));
                o.setTrackingNumber(rs.getString("tracking_number"));
                o.setShippingProvider(rs.getString("shipping_provider"));
                return o;
            }
        }
    }

    private String extractPincode(String address) {
        if (address == null) return null;
        Matcher m = PINCODE_RE.matcher(address);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Fetch order items with product name for logging.
     */
    private List<Map<String, Object>> fetchOrderItems(UUID orderId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql = "SELECT oi.quantity, oi.variant_label, oi.price, p.name AS product_name "
                + "FROM order_items oi LEFT JOIN products p ON oi.product_id = p.product_id "
                + "WHERE oi.order_id = ?";
        try (Connection conn = DBConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("productName", rs.getString("product_name"));
                    item.put("variantLabel", rs.getString("variant_label"));
                    item.put("quantity", rs.getInt("quantity"));
                    item.put("price", rs.getDouble("price"));
                    items.add(item);
                }
            }
        } catch (Exception e) {
            LOG.warn("[SHIP] Failed to fetch order items: {}", e.getMessage());
        }
        return items;
    }

    /**
     * Server-side variant weight calculation (mirrors frontend variantGrams logic).
     */
    private static final Pattern WEIGHT_RE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams)\\b");
    private static final Pattern PIECE_RE = Pattern.compile("\\b(pc|pcs|piece|pieces|no|nos|count|pack|packs)\\b");
    private static final int GRAMS_PER_ITEM = 300;
    private static final int PCS_GRAMS = 165;

    private int variantWeightGrams(String label) {
        if (label == null || label.isBlank()) return 0;
        String s = label.toLowerCase().trim();
        Matcher m = WEIGHT_RE.matcher(s);
        if (m.find()) {
            double value = Double.parseDouble(m.group(1));
            String unit = m.group(2);
            int grams = unit.startsWith("kg") ? (int) Math.round(value * 1000) : (int) Math.round(value);
            return Math.max(1, grams);
        }
        if (PIECE_RE.matcher(s).find()) return PCS_GRAMS;
        // Label exists but no weight pattern — treat as piece item
        return PCS_GRAMS;
    }
}
