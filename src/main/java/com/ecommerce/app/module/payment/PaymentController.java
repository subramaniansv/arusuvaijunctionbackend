package com.ecommerce.app.module.payment;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * POST /api/payment?action=initiate  - auth required; body matches /api/order body
 *                                      (shippingAddress, phone, optional item.* for buy-now)
 * POST /api/payment?action=verify    - auth required; body {orderId, razorpayOrderId,
 *                                      razorpayPaymentId, razorpaySignature}
 * POST /api/payment?action=webhook   - PUBLIC (Razorpay server-to-server). Verified by HMAC over raw body.
 */
@WebServlet("/api/payment")
public class PaymentController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentService service = new PaymentService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        if (action == null) action = "";
        try {
            switch (action) {
                case "initiate":
                    handleInitiate(request, response);
                    return;
                case "verify":
                    handleVerify(request, response);
                    return;
                case "webhook":
                    handleWebhook(request, response);
                    return;
                default:
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "unknown action", null, 400), response);
            }
        } catch (Exception e) {
            LOG.error("payment controller error action={}", action, e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "payment failed", null, 500), response);
        }
    }

    // ------------------------------------------------------------------
    // /api/payment?action=initiate
    // ------------------------------------------------------------------
    private void handleInitiate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
            return;
        }

        String shippingAddress = null;
        String phone = null;
        double shippingFee = 0.0;
        UUID buyNowProductId = null;
        UUID buyNowVariantId = null;
        int buyNowQuantity = 0;
        boolean buyNow = false;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body != null) {
                if (body.hasNonNull("shippingAddress")) shippingAddress = body.get("shippingAddress").asText();
                if (body.hasNonNull("phone")) phone = body.get("phone").asText();
                if (body.hasNonNull("shippingFee")) {
                    shippingFee = body.get("shippingFee").asDouble(0.0);
                    if (shippingFee < 0) shippingFee = 0.0;
                }
                JsonNode itemNode = body.get("item");
                if (itemNode != null && itemNode.isObject() && itemNode.hasNonNull("productId")) {
                    buyNow = true;
                    try {
                        buyNowProductId = UUID.fromString(itemNode.get("productId").asText());
                    } catch (IllegalArgumentException ex) {
                        SendResponseUtil.sendResponse(
                                new ApiResponse(false, "invalid item.productId", null, 400), response);
                        return;
                    }
                    if (itemNode.hasNonNull("variantId")) {
                        String vid = itemNode.get("variantId").asText();
                        if (vid != null && !vid.isBlank() && !"null".equalsIgnoreCase(vid)) {
                            try {
                                buyNowVariantId = UUID.fromString(vid);
                            } catch (IllegalArgumentException ex) {
                                SendResponseUtil.sendResponse(
                                        new ApiResponse(false, "invalid item.variantId", null, 400), response);
                                return;
                            }
                        }
                    }
                    buyNowQuantity = itemNode.hasNonNull("quantity") ? itemNode.get("quantity").asInt(1) : 1;
                    if (buyNowQuantity <= 0) buyNowQuantity = 1;
                }
            }
        } catch (Exception parseErr) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid payload", null, 400), response);
            return;
        }

        try {
            PaymentService.InitiationResponse init = buyNow
                    ? service.initiateBuyNowPayment(user.getUserId(), buyNowProductId, buyNowVariantId,
                            buyNowQuantity, shippingAddress, phone, shippingFee)
                    : service.initiateCartPayment(user.getUserId(), shippingAddress, phone, shippingFee);

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("orderId", init.orderId.toString());
            payload.put("razorpayOrderId", init.razorpayOrderId);
            payload.put("amount", init.amountInPaise); // paise
            payload.put("currency", init.currency);
            payload.put("keyId", init.keyId);
            payload.put("shippingFee", init.shippingFee); // ₹, for frontend display confirmation
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "payment initiated", payload, 200), response);
        } catch (RuntimeException re) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, re.getMessage(), null, 400), response);
        }
    }

    // ------------------------------------------------------------------
    // /api/payment?action=verify
    // ------------------------------------------------------------------
    private void handleVerify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
            return;
        }
        UUID orderId;
        String rpOrderId, rpPaymentId, signature;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            orderId = UUID.fromString(body.get("orderId").asText());
            rpOrderId = body.get("razorpayOrderId").asText();
            rpPaymentId = body.get("razorpayPaymentId").asText();
            signature = body.get("razorpaySignature").asText();
        } catch (Exception ex) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "invalid verification payload", null, 400), response);
            return;
        }
        try {
            var order = service.verifyAndCompletePayment(
                    user.getUserId(), orderId, rpOrderId, rpPaymentId, signature);
            if (order == null || order.getOrderId() == null) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "payment verification failed", null, 400), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "payment captured", order, 200), response);
        } catch (RuntimeException re) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, re.getMessage(), null, 400), response);
        }
    }

    // ------------------------------------------------------------------
    // /api/payment?action=webhook (PUBLIC, HMAC-authenticated)
    // ------------------------------------------------------------------
    private void handleWebhook(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Read raw body BEFORE Jackson parses anything - the HMAC payload is bytes.
        byte[] raw = request.getInputStream().readAllBytes();
        String body = new String(raw, StandardCharsets.UTF_8);
        String signature = request.getHeader("X-Razorpay-Signature");
        try {
            service.handleWebhook(body, signature);
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "ok", null, 200), response);
        } catch (RuntimeException re) {
            LOG.warn("webhook rejected: {}", re.getMessage());
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, re.getMessage(), null, 400), response);
        }
    }
}
