package com.ecommerce.app.module.affiliate;

import com.ecommerce.app.module.iam.config.ENVConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Affiliate / referral business logic.
 *
 * Flow:
 *   1. Affiliate creates a link ({@link #createLink}) and shares it.
 *   2. A visitor hits /api/affiliate/track?ref=CODE -> controller sets the
 *      aj_ref cookie (15d) and we bump the click counter here.
 *   3. On register OR login we bind the cookie's code to the user
 *      ({@link #bindReferral}) — this persists the attribution so it survives
 *      cookie deletion. First-touch wins; self-referral and same-person reuse
 *      are rejected here.
 *   4. When a referred user's order is PAID, {@link #awardForOrder} credits the
 *      affiliate's loyalty points (idempotent per order, one reward per
 *      attribution).
 *   5. On refund/cancel, {@link #reverseForOrder} claws the points back.
 *
 * Anti-abuse rules enforced:
 *   - You cannot refer yourself (affiliate_id == referred_user_id blocked).
 *   - A user can only ever be attributed ONCE (UNIQUE referred_user_id +
 *     first-touch guard) — so re-registering the "same person" can't re-earn.
 *   - The affiliate cannot be the SAME EMAIL/account as the buyer: we compare
 *     the normalized email of the two accounts and reject self-dealing even if
 *     they somehow end up as two user rows.
 *   - Points are only awarded on a PAID order, once, within the window.
 */
public class AffiliateService {
    private static final Logger LOG = LoggerFactory.getLogger(AffiliateService.class);

    public static final String REF_COOKIE = "aj_ref";

    private final AffiliateRepository repo = new AffiliateRepository();
    private final AffiliateSettingsRepository settingsRepo = new AffiliateSettingsRepository();

    // ---- link management ----------------------------------------------------

    public Map<String, Object> createLink(UUID affiliateId, UUID productId, String targetPath) {
        if (affiliateId == null) throw new IllegalArgumentException("authentication required");
        AffiliateLink link = repo.createLink(affiliateId, productId, targetPath);
        if (link == null) throw new RuntimeException("could not create affiliate link");
        link.setShareUrl(buildShareUrl(link));
        Map<String, Object> out = new HashMap<>();
        out.put("link", link);
        return out;
    }

    public Map<String, Object> myLinks(UUID affiliateId) {
        List<AffiliateLink> links = repo.listByAffiliate(affiliateId);
        for (AffiliateLink l : links) l.setShareUrl(buildShareUrl(l));
        long[] s = repo.summaryForAffiliate(affiliateId);
        Map<String, Object> out = new HashMap<>();
        out.put("links", links);
        out.put("totalClicks", s[0]);
        out.put("totalConversions", s[1]);
        out.put("totalPointsEarned", s[2]);
        return out;
    }

    /** Resolve a code to its link + record a click. Returns the redirect path, or null. */
    public AffiliateLink track(String code) {
        AffiliateLink link = repo.findActiveByCode(code);
        if (link == null) return null;
        repo.incrementClicks(link.getLinkId());
        return link;
    }

    public int windowDays() {
        int w = settingsRepo.get().getReferralWindowDays();
        return w > 0 ? w : 15;
    }

    // ---- attribution binding (called from register/login) -------------------

    /**
     * Bind the referral code (from the aj_ref cookie) to a now-authenticated
     * user. Best-effort: never throws into the auth flow. Returns true if a NEW
     * attribution was created.
     *
     * Guards:
     *   - code must resolve to an active link
     *   - no self-referral (affiliate == buyer user id)
     *   - no self-dealing by email (affiliate email == buyer email)
     *   - first-touch wins (skip if the user already has an attribution)
     *   - respect the 15-day window from first click (cookie timestamp)
     */
    public boolean bindReferral(UUID userId, String code, long firstClickEpochMs) {
        try {
            if (userId == null || code == null || code.isBlank()) return false;

            AffiliateLink link = repo.findActiveByCode(code);
            if (link == null) return false;

            // Self-referral by user id.
            if (userId.equals(link.getAffiliateId())) {
                LOG.info("affiliate self-referral blocked for user {}", userId);
                return false;
            }

            // Self-dealing by email: the buyer and affiliate must be different
            // real people. Compare normalized emails to stop "same email again".
            if (sameEmail(userId, link.getAffiliateId())) {
                LOG.info("affiliate same-email self-dealing blocked for user {}", userId);
                return false;
            }

            // First-touch wins: don't overwrite an existing binding.
            if (repo.findAttributionByUser(userId) != null) return false;

            long windowMs = (long) windowDays() * 24L * 60L * 60L * 1000L;
            long base = firstClickEpochMs > 0 ? firstClickEpochMs : System.currentTimeMillis();
            Timestamp expiresAt = new Timestamp(base + windowMs);
            if (expiresAt.getTime() <= System.currentTimeMillis()) return false; // already expired

            AffiliateAttribution attr = repo.createAttribution(userId, link.getLinkId(),
                    link.getAffiliateId(), expiresAt);
            return attr != null;
        } catch (Exception e) {
            LOG.warn("bindReferral failed (non-fatal): {}", e.getMessage());
            return false;
        }
    }

    // ---- points award (called from order PAID path) -------------------------

    /**
     * Award referral points to the affiliate for a buyer's PAID order. Runs in
     * its OWN transaction (keeps the call site simple and failures isolated).
     * Idempotent per order; consumes the attribution so an affiliate earns once
     * per referred user. Returns points awarded (0 if none).
     *
     * NOTE: orderTotal should be the merchandise total you want to reward on
     * (pass the order's paid amount).
     */
    public int awardForOrder(UUID buyerUserId, UUID orderId, double orderTotal) {
        if (buyerUserId == null || orderId == null || orderTotal <= 0) return 0;
        Connection c = null;
        try {
            c = com.ecommerce.app.config.DBConfig.getConnection();
            c.setAutoCommit(false);

            if (repo.conversionExistsForOrder(c, orderId)) { c.commit(); return 0; }

            AffiliateAttribution attr = repo.lockActiveAttribution(c, buyerUserId);
            if (attr == null) { c.commit(); return 0; }

            // Final safety net: never reward an affiliate for their own purchase.
            if (buyerUserId.equals(attr.getAffiliateId()) || sameEmail(buyerUserId, attr.getAffiliateId())) {
                c.commit();
                return 0;
            }

            int points = computeReferralPoints(orderTotal);
            if (points <= 0) { c.commit(); return 0; }

            repo.addPointsToAffiliate(c, attr.getAffiliateId(), points);
            repo.insertConversion(c, attr.getAttributionId(), orderId, attr.getAffiliateId(),
                    buyerUserId, orderTotal, points);
            repo.markAttributionConsumed(c, attr.getAttributionId(), true);

            c.commit();
            LOG.info("affiliate award: {} points to {} for order {}", points, attr.getAffiliateId(), orderId);
            return points;
        } catch (Exception e) {
            safeRollback(c);
            // 23505 => a concurrent award won the race; treat as no-op.
            LOG.warn("awardForOrder failed (non-fatal): {}", e.getMessage());
            return 0;
        } finally {
            closeQuietly(c);
        }
    }

    /** Claw back points when a rewarded order is refunded/cancelled. */
    public void reverseForOrder(UUID orderId) {
        if (orderId == null) return;
        Connection c = null;
        try {
            c = com.ecommerce.app.config.DBConfig.getConnection();
            c.setAutoCommit(false);
            var conv = repo.findConversionByOrder(c, orderId);
            if (conv == null || conv.reversed) { c.commit(); return; }
            repo.subtractPointsFromAffiliate(c, conv.affiliateId, conv.points);
            repo.markConversionReversed(c, conv.conversionId);
            // Free the attribution so a future genuine order could still reward.
            repo.markAttributionConsumed(c, conv.attributionId, false);
            c.commit();
            LOG.info("affiliate reversal: -{} points from {} for order {}", conv.points, conv.affiliateId, orderId);
        } catch (Exception e) {
            safeRollback(c);
            LOG.warn("reverseForOrder failed (non-fatal): {}", e.getMessage());
        } finally {
            closeQuietly(c);
        }
    }

    // ---- points rule --------------------------------------------------------

    /**
     * Flat points win when configured (> 0); otherwise rate * orderTotal,
     * floored. Returns 0 when the feature is unconfigured (referral disabled).
     */
    int computeReferralPoints(double orderTotal) {
        AffiliateSettings s = settingsRepo.get();
        if (s.getReferralFlatPoints() > 0) return s.getReferralFlatPoints();
        BigDecimal rate = s.getReferralPointRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return rate.multiply(BigDecimal.valueOf(orderTotal))
                   .setScale(0, RoundingMode.DOWN)
                   .intValueExact();
    }

    // ---- helpers ------------------------------------------------------------

    /** True when the two user ids resolve to the same normalized email. */
    private boolean sameEmail(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        String ea = emailOf(a), eb = emailOf(b);
        if (ea == null || eb == null) return false;
        return normalizeEmail(ea).equals(normalizeEmail(eb));
    }

    private String emailOf(UUID userId) {
        String sql = "SELECT email FROM users WHERE user_id = ?";
        try (Connection c = com.ecommerce.app.config.DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("email");
            }
        } catch (Exception e) {
            LOG.warn("emailOf failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Normalize an email for equality: lowercase + trim, and for gmail collapse
     * dots and strip +tags in the local part (so john.doe+promo@gmail.com and
     * johndoe@gmail.com are treated as the same person — a common referral abuse).
     */
    static String normalizeEmail(String email) {
        if (email == null) return "";
        String e = email.trim().toLowerCase();
        int at = e.indexOf('@');
        if (at <= 0) return e;
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus >= 0) local = local.substring(0, plus);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }

    private String buildShareUrl(AffiliateLink link) {
        String base = ENVConfig.get("APP_HOME_URL");
        if (base == null || base.isBlank()) base = ENVConfig.get("APP_BASE_URL");
        if (base == null || base.isBlank()) base = "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String path = link.getTargetPath() == null ? "/" : link.getTargetPath();
        String sep = path.contains("?") ? "&" : "?";
        return base + path + sep + "ref=" + link.getCode();
    }

    private void safeRollback(Connection c) {
        if (c != null) try { c.rollback(); } catch (Exception ignore) { }
    }

    private void closeQuietly(Connection c) {
        if (c != null) try { c.setAutoCommit(true); c.close(); } catch (Exception ignore) { }
    }
}
