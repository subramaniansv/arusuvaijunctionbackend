package com.ecommerce.app.module.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebServlet("/api/order")
public class OrderController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(OrderController.class);

    OrderService service = new OrderService();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }

            // Body:
            //   Cart checkout : { "shippingAddress": "...", "phone": "..." }
            //   Buy-now       : { "shippingAddress": "...", "phone": "...",
            //                     "item": { "productId": "...", "quantity": 1,
            //                               "variantId": "..." (optional) } }
            String shippingAddress = null;
            String phone = null;
            UUID buyNowProductId = null;
            UUID buyNowVariantId = null;
            int buyNowQuantity = 0;
            boolean buyNow = false;
            try {
                JsonNode body = MAPPER.readTree(request.getInputStream());
                if (body != null) {
                    if (body.hasNonNull("shippingAddress")) {
                        shippingAddress = body.get("shippingAddress").asText();
                    }
                    if (body.hasNonNull("phone")) {
                        phone = body.get("phone").asText();
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
                        buyNowQuantity = itemNode.hasNonNull("quantity")
                                ? itemNode.get("quantity").asInt(1)
                                : 1;
                        if (buyNowQuantity <= 0) buyNowQuantity = 1;
                    }
                }
            } catch (Exception parseErr) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, "invalid checkout payload", null, 400), response);
                return;
            }

            try {
                Order placed = buyNow
                        ? service.checkoutSingle(user.getUserId(), buyNowProductId, buyNowVariantId,
                                buyNowQuantity, shippingAddress, phone)
                        : service.checkout(user.getUserId(), shippingAddress, phone);
                if (placed == null || placed.getOrderId() == null) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "checkout failed", null, 400), response);
                    return;
                }
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "order placed", placed, 200), response);
            } catch (RuntimeException re) {
                SendResponseUtil.sendResponse(
                        new ApiResponse(false, re.getMessage(), null, 400), response);
            }
        } catch (Exception e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "order not created try again at exception", null, 500), response);
            LOG.error("exception", e);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthUser user = AuthContext.get();
            if (user == null || user.getUserId() == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), response);
                return;
            }
            String orderIdParam = request.getParameter("orderID");
            if (orderIdParam != null) {
                UUID orderId;
                try {
                    orderId = UUID.fromString(orderIdParam);
                } catch (IllegalArgumentException ex) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "invalid orderID", null, 400), response);
                    return;
                }
                Order order = service.getOrderById(orderId);
                if (order == null || order.getOrderId() == null) {
                    SendResponseUtil.sendResponse(new ApiResponse(false, "order not found", null, 404), response);
                } else {
                    SendResponseUtil.sendResponse(new ApiResponse(true, "order fetched", order, 200), response);
                }
            } else {
                int limit = 10;
                int offset = 0;
                try {
                    if (request.getParameter("limit") != null) {
                        limit = Integer.parseInt(request.getParameter("limit"));
                    }
                    if (request.getParameter("offset") != null) {
                        offset = Integer.parseInt(request.getParameter("offset"));
                    }
                } catch (NumberFormatException nfe) {
                    SendResponseUtil.sendResponse(
                            new ApiResponse(false, "limit and offset must be integers", null, 400), response);
                    return;
                }
                SendResponseUtil.sendResponse(
                        new ApiResponse(true, "orders fetched for user " + user.getUserId().toString(),
                                service.getOrderByUserId(limit, offset), 200),
                        response);
            }
        } catch (Exception e) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "order exception at order controller", null, 500), response);
            LOG.error("exception", e);
        }
    }
}

/*
 * ---------------------------------------------------------------------------
 * LEGACY: manual order creation (client-supplied order payload).
 *
 * Replaced by cart-based checkout (see doPost above). Kept here for reference
 * in case we ever need to re-enable a direct "buy now" path that bypasses the
 * cart. Restore by:
 *   1. Re-adding the `OrderConverterUtil` import.
 *   2. Pasting this branch back into doPost above the checkout branch,
 *      gated on a request flag (e.g. ?action=create).
 *
 *   // Manual order create: body is a full Order JSON with order_items.
 *   Order order = OrderConverterUtil.requestToDto(request);
 *   if (order == null) {
 *       SendResponseUtil.sendResponse(
 *           new ApiResponse(false, "invalid order payload", null, 400), response);
 *       return;
 *   }
 *   // never trust client-supplied userId
 *   order.setUserId(user.getUserId());
 *
 *   Order createdOrder = service.create(order);
 *   if (createdOrder == null || createdOrder.getOrderId() == null) {
 *       SendResponseUtil.sendResponse(
 *           new ApiResponse(false, "order not created try again", null, 400), response);
 *       return;
 *   }
 *   SendResponseUtil.sendResponse(
 *       new ApiResponse(true, "order created successfully", createdOrder, 200), response);
 *
 * The backing service method `OrderService.create(Order)` is still present but
 * is no longer reached from any controller. Safe to delete once we're sure we
 * won't need the direct path.
 * ---------------------------------------------------------------------------
 */
