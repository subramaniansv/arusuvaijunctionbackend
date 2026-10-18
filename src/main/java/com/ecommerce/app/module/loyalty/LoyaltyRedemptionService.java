package com.ecommerce.app.module.loyalty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loyalty point redemption logic.
 *
 * The admin sets how much 1 point is worth in real cash (default ₹10). Points
 * are then spendable two ways:
 *
 *   ONLINE  — at checkout the customer chooses how many points to use; the
 *             value is deducted from the final payable amount automatically,
 *             inside the payment transaction ({@link #applyOnlineRedemption}).
 *   OFFLINE — the customer requests a redemption from their card page; the
 *             points are reserved immediately and an admin approves (confirm
 *             the counter discount) or rejects (refund the points).
 *
 * Clamping rule everywhere: a customer can never spend more points than their
 * balance, nor more cash value than the order/bill total.
 */
public class LoyaltyRedemptionService {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyRedemptionService.class);

    private final LoyaltyRedemptionRepository repo = new LoyaltyRedemptionRepository();
    private final LoyaltyRepository cardRepo = new LoyaltyRepository();

    // ---- settings -----------------------------------------------------------

    public Map<String, Object> settings() {
        Map<String, Object> out = new HashMap<>();
        out.put("pointValueInr", repo.getPointValue());
        return out;
    }

    public boolean updatePointValue(BigDecimal value, UUID adminId) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("point value must be greater than 0");
        }
        return repo.setPointValue(value.setScale(2, RoundingMode.HALF_UP), adminId);
    }

    // ---- online (checkout) --------------------------------------------------

    /**
     * Clamp a requested online redemption to what the user can actually spend:
     * no more than the balance, and no more cash value than the order total.
     * Returns the number of points that may be spent (0 if none/invalid).
     */
    public int clampOnlinePoints(UUID userId, int requestedPoints, double orderTotal) {
        if (userId == null || requestedPoints <= 0 || orderTotal <= 0) return 0;
        LoyaltyCard card = cardRepo.findCardByUser(userId);
        int balance = card != null ? card.getPoints() : 0;
        if (balance <= 0) return 0;

        BigDecimal pv = repo.getPointValue();
        if (pv.compareTo(BigDecimal.ZERO) <= 0) return 0;

        // Max points whose cash value does not exceed the order total.
        int maxByTotal = BigDecimal.valueOf(orderTotal)
                .divide(pv, 0, RoundingMode.DOWN)
                .intValueExact();
        return Math.max(0, Math.min(requestedPoints, Math.min(balance, maxByTotal)));
    }

    /**
     * Apply an online redemption inside the caller's payment transaction.
     * Points are deducted atomically with the order. Returns the redemption
     * (with cashValue to subtract), or null if it could not be applied.
     */
    public LoyaltyRedemption applyOnlineRedemption(Connection c, UUID userId, int points, UUID orderId) {
        try {
            if (points <= 0) return null;
            BigDecimal pv = repo.getPointValue();
            return repo.redeemOnline(c, userId, points, pv, orderId);
        } catch (Exception e) {
            LOG.warn("applyOnlineRedemption failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * A server-authoritative quote for the checkout UI: given a requested
     * point count and the order total, returns the clamped points, the cash
     * discount, and the resulting payable amount.
     */
    public Map<String, Object> quote(UUID userId, int requestedPoints, double orderTotal) {
        LoyaltyCard card = cardRepo.findCardByUser(userId);
        int balance = card != null ? card.getPoints() : 0;
        BigDecimal pv = repo.getPointValue();

        int maxUsable = clampOnlinePoints(userId, balance, orderTotal); // clamp balance to total
        int pointsToUse = clampOnlinePoints(userId, requestedPoints, orderTotal);
        BigDecimal discount = pv.multiply(BigDecimal.valueOf(pointsToUse))
                .setScale(2, RoundingMode.HALF_UP);
        // Keep the subtraction in BigDecimal so fractional point values (e.g.
        // 2.50) never introduce sub-paisa drift; convert to double only for the
        // JSON response.
        double payable = BigDecimal.valueOf(orderTotal)
                .subtract(discount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        Map<String, Object> out = new HashMap<>();
        out.put("balance", balance);
        out.put("pointValueInr", pv);
        out.put("maxUsablePoints", maxUsable);
        out.put("pointsToUse", pointsToUse);
        out.put("discount", discount);
        out.put("payable", payable);
        return out;
    }

    // ---- offline ------------------------------------------------------------

    public LoyaltyRedemption requestOffline(UUID userId, int points, String note) {
        if (points <= 0) throw new IllegalArgumentException("choose how many points to use");
        LoyaltyCard card = cardRepo.findCardByUser(userId);
        int balance = card != null ? card.getPoints() : 0;
        if (points > balance) {
            throw new IllegalArgumentException("not enough points (balance: " + balance + ")");
        }
        BigDecimal pv = repo.getPointValue();
        LoyaltyRedemption r = repo.requestOffline(userId, points, pv, note);
        if (r == null) {
            throw new IllegalArgumentException("not enough points");
        }

        // Fire-and-forget: alert admins that a points-usage (offline redemption)
        // request needs approval.
        try {
            com.ecommerce.app.module.push.PushService.get().sendToAdmins(
                    "Points usage approval \uD83C\uDFAB",
                    "A customer wants to redeem " + points + " point(s). Approve or reject it.",
                    "/admin/loyalty/redemptions");
        } catch (Exception pushErr) {
            LOG.warn("points-usage admin push failed (non-fatal): {}", pushErr.getMessage());
        }

        return r;
    }

    public boolean decideOffline(UUID redemptionId, boolean approve, UUID adminId, String note) {
        if (redemptionId == null) return false;
        return repo.decideOffline(redemptionId, approve, adminId, note);
    }

    // ---- feeds --------------------------------------------------------------

    public Map<String, Object> myRedemptions(UUID userId, int limit, int offset) {
        if (limit <= 0 || limit > 100) limit = 20;
        if (offset < 0) offset = 0;
        List<LoyaltyRedemption> rows = repo.listUserRedemptions(userId, limit, offset);
        Map<String, Object> out = new HashMap<>();
        out.put("redemptions", rows);
        return out;
    }

    public Map<String, Object> list(int limit, int offset, String status) {
        if (limit <= 0 || limit > 100) limit = 20;
        if (offset < 0) offset = 0;
        String s = status == null ? null : status.trim().toUpperCase();
        List<LoyaltyRedemption> rows = repo.listRedemptions(limit, offset, s);
        Map<String, Object> out = new HashMap<>();
        out.put("redemptions", rows);
        out.put("limit", limit);
        out.put("offset", offset);
        return out;
    }
}
