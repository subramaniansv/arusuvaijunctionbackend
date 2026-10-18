package com.ecommerce.app.module.delivery;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.ecommerce.app.module.order.OrderRepository;
import com.ecommerce.app.module.order.OrderStatus;
import com.ecommerce.app.config.DBConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;

/**
 * Inbound Rapido status webhook: POST /api/integrations/rapido/webhook
 *
 * Verifies the HMAC signature (X-Rapido-Signature) over the RAW body, maps the
 * provider status onto our order lifecycle, and updates delivery_job.
 *
 * This endpoint must be publicly reachable (no JWT). It is secured by the
 * shared-secret signature instead — add it to the auth filter allowlist.
 */
@WebServlet("/api/integrations/rapido/webhook")
public class RapidoWebhookController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(RapidoWebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RapidoClient rapido = RapidoClient.get();
    private final DeliveryJobRepository jobRepo = new DeliveryJobRepository();
    private final OrderRepository orderRepo = new OrderRepository();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String rawBody = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String signature = req.getHeader("X-Rapido-Signature");

        if (!rapido.verifyWebhookSignature(rawBody, signature)) {
            LOG.warn("rejected rapido webhook: bad signature");
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid signature", null, 401), resp);
            return;
        }

        try {
            JsonNode root = MAPPER.readTree(rawBody);
            String providerOrderId = root.path("order_id").asText(root.path("id").asText(null));
            String providerStatus = root.path("status").asText(null);
            if (providerOrderId == null || providerStatus == null) {
                SendResponseUtil.sendResponse(new ApiResponse(true, "ignored", null, 200), resp);
                return;
            }
            String riderName = root.path("rider").path("name").asText(null);
            String riderPhone = root.path("rider").path("phone").asText(null);

            jobRepo.updateStatusByProviderOrderId(providerOrderId, providerStatus,
                    riderName, riderPhone, rawBody);

            // Map terminal provider states onto the order lifecycle.
            OrderStatus mapped = mapStatus(providerStatus);
            if (mapped != null) {
                UUID orderId = jobRepo.findOrderIdByProviderOrderId(providerOrderId);
                if (orderId != null) {
                    try (Connection c = DBConfig.getConnection()) {
                        orderRepo.updateStatus(c, mapped, orderId);
                    }
                }
            }

            // Always 200 so Rapido does not retry a processed event.
            SendResponseUtil.sendResponse(new ApiResponse(true, "ok", null, 200), resp);
        } catch (Exception e) {
            LOG.error("error processing rapido webhook", e);
            // 500 makes Rapido retry — appropriate for transient failures.
            SendResponseUtil.sendResponse(new ApiResponse(false, "processing error", null, 500), resp);
        }
    }

    /**
     * Map Rapido delivery status -> our OrderStatus. Non-terminal states
     * (rider_assigned, picked_up, out_for_delivery) are tracked on
     * delivery_job only and return null here.
     */
    private OrderStatus mapStatus(String s) {
        if (s == null) return null;
        return switch (s.toUpperCase()) {
            case "PICKED_UP", "OUT_FOR_DELIVERY", "IN_TRANSIT" -> OrderStatus.SHIPPED;
            case "DELIVERED", "COMPLETED" -> OrderStatus.DELIVERED;
            case "CANCELLED", "RETURNED", "FAILED" -> OrderStatus.CANCELLED;
            default -> null; // RIDER_ASSIGNED, CREATED, etc. -> job-only
        };
    }
}
