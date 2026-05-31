package com.ecommerce.app.module.shipping;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.ecommerce.app.module.payment.ShippingCalculator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Public shipping endpoints (authenticated users).
 *
 * GET /api/shipping?action=check&pincode=600001
 *   -> { serviceable, codAvailable, estimatedDays, district }
 *
 * GET /api/shipping?action=rate&pincode=600001&weightGrams=500&subtotal=350
 *   -> { shippingFee, zone, chargedWeightGrams, freeShipping }
 *
 * GET /api/shipping?action=track&orderId=...
 *   -> { waybill, status, statusDate, expectedDeliveryDate, scans: [...] }
 */
@WebServlet("/api/shipping")
public class ShippingController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(ShippingController.class);
    private final ShippingService shippingService = new ShippingService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        if (action == null || action.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Missing 'action' parameter (check|rate|track)", null, 400), response);
            return;
        }

        try {
            switch (action) {
                case "check" -> handleCheck(request, response);
                case "rate" -> handleRate(request, response);
                case "track" -> handleTrack(request, response);
                default -> SendResponseUtil.sendResponse(
                        new ApiResponse(false, "Unknown action: " + action, null, 400), response);
            }
        } catch (Exception e) {
            LOG.error("Shipping endpoint error: action={}", action, e);
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Shipping service error: " + e.getMessage(), null, 500), response);
        }
    }

    private void handleCheck(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String pincode = request.getParameter("pincode");
        if (pincode == null || !pincode.matches("\\d{6}")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Invalid pincode", null, 400), response);
            return;
        }

        ServiceabilityResult result = shippingService.checkServiceability(pincode);
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "ok", Map.of(
                        "serviceable", result.serviceable(),
                        "codAvailable", result.codAvailable(),
                        "estimatedDays", result.estimatedDays(),
                        "district", result.district() != null ? result.district() : ""
                ), 200), response);
    }

    private void handleRate(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String pincode = request.getParameter("pincode");
        String weightStr = request.getParameter("weightGrams");
        String subtotalStr = request.getParameter("subtotal");

        if (pincode == null || !pincode.matches("\\d{6}")) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Invalid pincode", null, 400), response);
            return;
        }
        int weightGrams = 300; // default
        if (weightStr != null && !weightStr.isBlank()) {
            weightGrams = Integer.parseInt(weightStr);
        }
        double subtotal = 0;
        if (subtotalStr != null && !subtotalStr.isBlank()) {
            subtotal = Double.parseDouble(subtotalStr);
        }

        // Build a synthetic address string for the fallback calculator
        String syntheticAddress = "Pin: " + pincode;
        double fee = shippingService.calculateRate(syntheticAddress, pincode, weightGrams, subtotal);
        boolean freeShipping = subtotal >= ShippingCalculator.FREE_ABOVE_INR;

        SendResponseUtil.sendResponse(
                new ApiResponse(true, "ok", Map.of(
                        "shippingFee", fee,
                        "freeShipping", freeShipping
                ), 200), response);
    }

    private void handleTrack(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String waybill = request.getParameter("waybill");
        if (waybill == null || waybill.isBlank()) {
            SendResponseUtil.sendResponse(
                    new ApiResponse(false, "Missing 'waybill' parameter", null, 400), response);
            return;
        }

        TrackingResult result = shippingService.trackShipment(waybill);
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "ok", result, 200), response);
    }
}
