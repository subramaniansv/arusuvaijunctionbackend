package com.ecommerce.app.module.quick;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public (authenticated) quick-delivery endpoints.
 *
 * GET  /api/quick?action=serviceability&pincode=627006&lat=..&lng=..
 *   -> { serviceable, storeId, storeName, distanceKm, message }
 *
 * POST /api/quick?action=quote
 *   body: { "pincode":"627006", "lat":.., "lng":.., "weightGrams":600 }
 *   -> { quoteId, serviceable, riderAvailable, fee, etaMin, distanceKm, message }
 */
@WebServlet("/api/quick")
public class QuickDeliveryController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(QuickDeliveryController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final QuickDeliveryService service = new QuickDeliveryService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), resp);
            return;
        }
        String action = req.getParameter("action");
        if (!"serviceability".equals(action)) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "Use action=serviceability", null, 400), resp);
            return;
        }
        try {
            String pincode = req.getParameter("pincode");
            double lat = Double.parseDouble(req.getParameter("lat"));
            double lng = Double.parseDouble(req.getParameter("lng"));
            QuickDeliveryService.Serviceability s = service.resolveStore(pincode, lat, lng);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("serviceable", s.serviceable());
            data.put("storeId", s.storeId());
            data.put("storeName", s.storeName());
            data.put("distanceKm", round2(s.distanceKm()));
            data.put("message", s.message());
            SendResponseUtil.sendResponse(new ApiResponse(true, "ok", data, 200), resp);
        } catch (Exception e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid lat/lng/pincode", null, 400), resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AuthUser user = AuthContext.get();
        if (user == null || user.getUserId() == null) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "missing userid", null, 401), resp);
            return;
        }
        String action = req.getParameter("action");
        if (!"quote".equals(action)) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "Use action=quote", null, 400), resp);
            return;
        }
        try {
            JsonNode body = MAPPER.readTree(req.getInputStream());
            String pincode = body.path("pincode").asText(null);
            double lat = body.path("lat").asDouble();
            double lng = body.path("lng").asDouble();
            int weightGrams = body.path("weightGrams").asInt(500);

            QuickDeliveryService.Quote q = service.createQuote(user.getUserId(), pincode, lat, lng, weightGrams);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("quoteId", q.quoteId());
            data.put("serviceable", q.serviceable());
            data.put("riderAvailable", q.riderAvailable());
            data.put("storeId", q.storeId());
            data.put("fee", round2(q.fee()));
            data.put("etaMin", q.etaMin());
            data.put("distanceKm", round2(q.distanceKm()));
            data.put("message", q.message());
            SendResponseUtil.sendResponse(new ApiResponse(true, "ok", data, 200), resp);
        } catch (Exception e) {
            LOG.error("quote error", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "could not create quote", null, 400), resp);
        }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
