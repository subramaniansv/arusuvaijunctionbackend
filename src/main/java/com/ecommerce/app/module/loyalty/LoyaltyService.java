package com.ecommerce.app.module.loyalty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loyalty business logic.
 *
 * Offline flow (QR):
 *   1. User scans the store QR -> frontend calls {@link #scan(UUID)} with the
 *      authenticated user id (the AuthorizationFilter guarantees a real JWT;
 *      an unauthenticated scan is bounced to /login first).
 *   2. We lazily create the user's card and raise a PENDING check-in for today.
 *   3. The PENDING scan shows up live in the admin console feed. The admin
 *      approves or rejects it there (no email is sent). On approval the visit
 *      is tracked for that day and a point is awarded; on rejection it is
 *      discarded and the customer can scan again.
 *
 * Online flow (ORDER):
 *   - {@link #recordOrderVisit} is called right after a successful checkout and
 *     auto-APPROVES a visit for the order day (no admin step needed).
 *
 * Both paths converge on the same {@code loyalty_checkins} ledger, and the
 * one-visit-per-day unique index keeps the count honest.
 */
public class LoyaltyService {
    private static final Logger LOG = LoggerFactory.getLogger(LoyaltyService.class);

    private final LoyaltyRepository repo = new LoyaltyRepository();

    /**
     * Handle a QR scan by an authenticated user. Idempotent for the day: a
     * second scan while a PENDING/APPROVED visit already exists returns the
     * existing state instead of creating a duplicate.
     *
     * @return a result map: { card, checkin, alreadyCheckedIn }
     */
    public Map<String, Object> scan(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("authentication required");

        LoyaltyCard card = repo.getOrCreateCard(userId);
        if (card == null) throw new RuntimeException("could not initialise loyalty card");

        Map<String, Object> out = new HashMap<>();
        out.put("card", card);

        if (repo.hasVisitToday(userId)) {
            out.put("alreadyCheckedIn", true);
            out.put("message", "You're already checked in for today.");
            return out;
        }

        LoyaltyCheckin ci = new LoyaltyCheckin();
        ci.setCardId(card.getCardId());
        ci.setUserId(userId);
        ci.setSource("QR");
        ci.setStatus("PENDING");
        LoyaltyCheckin saved = repo.insertCheckin(ci);
        if (saved == null) {
            // Lost a race with a concurrent scan -> treat as already checked in.
            out.put("alreadyCheckedIn", true);
            out.put("message", "You're already checked in for today.");
            return out;
        }

        out.put("alreadyCheckedIn", false);
        out.put("checkin", saved);
        out.put("message", "Check-in received. Waiting for staff approval.");

        // Fire-and-forget: alert admins that a check-in needs approval.
        try {
            com.ecommerce.app.module.push.PushService.get().sendToAdmins(
                    "Check-in approval \uD83D\uDCCD",
                    "A customer checked in and is waiting for approval.",
                    "/admin/loyalty");
        } catch (Exception pushErr) {
            LOG.warn("check-in admin push failed (non-fatal): {}", pushErr.getMessage());
        }

        return out;
    }

    /**
     * Auto-record an APPROVED visit for an online order. Best-effort: never
     * throws into the checkout flow. Safe to call multiple times a day (the
     * unique index collapses duplicates to a single visit).
     */
    public void recordOrderVisit(UUID userId, UUID orderId) {
        recordOrderVisit(userId, orderId, 0.0);
    }

    /**
     * Auto-record an APPROVED visit for an online order AND award the buyer
     * their order points. Backward compatible: when the admin-configured
     * {@code order_point_rate} is 0 (the default), the legacy behaviour of
     * "+1 point per order visit" is preserved. When a rate > 0 is set, the
     * buyer instead earns {@code floor(rate * orderTotal)} points for the order
     * (minimum 1 so a paid order never earns nothing).
     */
    public void recordOrderVisit(UUID userId, UUID orderId, double orderTotal) {
        try {
            if (userId == null) return;
            if (repo.hasVisitToday(userId)) return;

            LoyaltyCard card = repo.getOrCreateCard(userId);
            if (card == null) return;

            LoyaltyCheckin ci = new LoyaltyCheckin();
            ci.setCardId(card.getCardId());
            ci.setUserId(userId);
            ci.setSource("ORDER");
            ci.setStatus("APPROVED");
            ci.setOrderId(orderId);
            ci.setNote("Auto check-in from online order");
            LoyaltyCheckin saved = repo.insertCheckin(ci);
            if (saved != null) {
                // Inserted as APPROVED directly; bump counters via approve()'s
                // sibling path would double-count, so update counters here.
                bumpCardForAutoApproved(card.getCardId(), computeOrderPoints(orderTotal));
            }
        } catch (Exception e) {
            LOG.warn("recordOrderVisit failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Buyer order points: legacy +1 when no rate configured, otherwise
     * floor(rate * total) with a floor of 1 point for any paid order.
     */
    int computeOrderPoints(double orderTotal) {
        try {
            var settings = new com.ecommerce.app.module.affiliate.AffiliateSettingsRepository().get();
            java.math.BigDecimal rate = settings.getOrderPointRate();
            if (rate == null || rate.compareTo(java.math.BigDecimal.ZERO) <= 0 || orderTotal <= 0) {
                return 1; // backward-compatible default
            }
            int pts = rate.multiply(java.math.BigDecimal.valueOf(orderTotal))
                          .setScale(0, java.math.RoundingMode.DOWN)
                          .intValueExact();
            return Math.max(1, pts);
        } catch (Exception e) {
            return 1;
        }
    }

    public Map<String, Object> myCard(UUID userId) {
        LoyaltyCard card = repo.getOrCreateCard(userId);
        List<LoyaltyCheckin> history = repo.listUserCheckins(userId, 50, 0);
        Map<String, Object> out = new HashMap<>();
        out.put("card", card);
        out.put("history", history);
        out.put("checkedInToday", repo.hasVisitToday(userId));
        return out;
    }

    // ---- admin --------------------------------------------------------------

    public Map<String, Object> list(int limit, int offset, String status) {
        if (limit <= 0 || limit > 100) limit = 20;
        if (offset < 0) offset = 0;
        String s = status == null ? null : status.trim().toUpperCase();
        List<LoyaltyCheckin> rows = repo.listCheckins(limit, offset, s);
        int total = repo.countCheckins(s);
        Map<String, Object> out = new HashMap<>();
        out.put("checkins", rows);
        out.put("total", total);
        out.put("limit", limit);
        out.put("offset", offset);
        return out;
    }

    /**
     * Approve a PENDING check-in. When the admin supplies the customer's
     * in-store {@code purchaseAmount}, the visit earns points via the SAME
     * economics as an online order (loyalty_settings.order_point_rate). When no
     * amount is given, the legacy behaviour applies: a flat +1 point per visit.
     *
     * @param purchaseAmount nullable in-store spend recorded by the admin.
     */
    public boolean approve(UUID checkinId, UUID adminId, java.math.BigDecimal purchaseAmount) {
        if (checkinId == null) return false;
        double amount = (purchaseAmount != null && purchaseAmount.doubleValue() > 0)
                ? purchaseAmount.doubleValue() : 0.0;
        int points = computeOrderPoints(amount);
        return repo.approve(checkinId, adminId, purchaseAmount, points);
    }

    /** Backward-compatible overload: approve with no recorded spend (+1 point). */
    public boolean approve(UUID checkinId, UUID adminId) {
        return approve(checkinId, adminId, null);
    }

    public boolean reject(UUID checkinId, UUID adminId, String note) {
        if (checkinId == null) return false;
        return repo.reject(checkinId, adminId, note);
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * For ORDER-sourced visits we insert straight as APPROVED, so we top up the
     * card counters directly instead of going through {@link LoyaltyRepository#approve}
     * (which only transitions PENDING rows).
     */
    private void bumpCardForAutoApproved(UUID cardId, int points) {
        int pts = Math.max(0, points);
        try (var c = com.ecommerce.app.config.DBConfig.getConnection();
             var ps = c.prepareStatement(
                     "UPDATE loyalty_cards SET total_visits = total_visits + 1, "
                     + "points = points + ?, updated_at = CURRENT_TIMESTAMP WHERE card_id = ?")) {
            ps.setInt(1, pts);
            ps.setObject(2, cardId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOG.warn("bumpCardForAutoApproved failed: {}", e.getMessage());
        }
    }

}
