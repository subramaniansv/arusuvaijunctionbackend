package com.ecommerce.app.module.affiliate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.util.SendResponseUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin console for affiliate + order->points rate configuration. Admin-only.
 *
 *   GET /api/admin/affiliate/settings
 *       -> { orderPointRate, referralPointRate, referralFlatPoints, referralWindowDays }
 *   PUT /api/admin/affiliate/settings
 *       body (any subset): { orderPointRate, referralPointRate, referralFlatPoints, referralWindowDays }
 *
 * These live on the shared loyalty_settings singleton so all point economics
 * (point->cash value + order->points + referral->points) are managed together.
 */
@WebServlet(urlPatterns = { "/api/admin/affiliate/settings" })
@RequiresRole("Admin")
public class AdminAffiliateController extends HttpServlet {
    private static final Logger LOG = LoggerFactory.getLogger(AdminAffiliateController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AffiliateSettingsRepository repo = new AffiliateSettingsRepository();

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        SendResponseUtil.sendResponse(
                new ApiResponse(true, "affiliate settings", toMap(repo.get()), 200), response);
    }

    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        AuthUser admin = AuthContext.get();
        UUID adminId = admin != null ? admin.getUserId() : null;
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());
            if (body == null) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid payload", null, 400), response);
                return;
            }
            // Start from current so a partial update only touches provided fields.
            AffiliateSettings s = repo.get();

            if (body.hasNonNull("orderPointRate")) {
                BigDecimal v = body.get("orderPointRate").decimalValue();
                if (v.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("orderPointRate must be >= 0");
                s.setOrderPointRate(v);
            }
            if (body.hasNonNull("referralPointRate")) {
                BigDecimal v = body.get("referralPointRate").decimalValue();
                if (v.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("referralPointRate must be >= 0");
                s.setReferralPointRate(v);
            }
            if (body.hasNonNull("referralFlatPoints")) {
                int v = body.get("referralFlatPoints").asInt();
                if (v < 0) throw new IllegalArgumentException("referralFlatPoints must be >= 0");
                s.setReferralFlatPoints(v);
            }
            if (body.hasNonNull("referralWindowDays")) {
                int v = body.get("referralWindowDays").asInt();
                if (v <= 0) throw new IllegalArgumentException("referralWindowDays must be > 0");
                s.setReferralWindowDays(v);
            }

            boolean ok = repo.update(s, adminId);
            if (!ok) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "could not update", null, 500), response);
                return;
            }
            SendResponseUtil.sendResponse(
                    new ApiResponse(true, "affiliate settings updated", toMap(repo.get()), 200), response);
        } catch (IllegalArgumentException e) {
            SendResponseUtil.sendResponse(new ApiResponse(false, e.getMessage(), null, 400), response);
        } catch (Exception e) {
            LOG.error("exception at admin affiliate settings update ", e);
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid payload", null, 400), response);
        }
    }

    private Map<String, Object> toMap(AffiliateSettings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderPointRate", s.getOrderPointRate());
        m.put("referralPointRate", s.getReferralPointRate());
        m.put("referralFlatPoints", s.getReferralFlatPoints());
        m.put("referralWindowDays", s.getReferralWindowDays());
        return m;
    }
}
