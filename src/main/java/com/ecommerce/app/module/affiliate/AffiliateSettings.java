package com.ecommerce.app.module.affiliate;

import java.math.BigDecimal;

/**
 * Admin-configurable rates, stored on the loyalty_settings singleton row.
 *
 *   orderPointRate      points a BUYER earns per ₹1 of order total.
 *   referralPointRate   points the AFFILIATE earns per ₹1 of a referred order.
 *   referralFlatPoints  flat points per referred order; when > 0 it OVERRIDES
 *                       referralPointRate (simple "N points per referral").
 *   referralWindowDays  attribution window length (default 15).
 */
public class AffiliateSettings {
    private BigDecimal orderPointRate = BigDecimal.ZERO;
    private BigDecimal referralPointRate = BigDecimal.ZERO;
    private int referralFlatPoints = 0;
    private int referralWindowDays = 15;

    public BigDecimal getOrderPointRate() { return orderPointRate; }
    public void setOrderPointRate(BigDecimal v) { this.orderPointRate = v; }
    public BigDecimal getReferralPointRate() { return referralPointRate; }
    public void setReferralPointRate(BigDecimal v) { this.referralPointRate = v; }
    public int getReferralFlatPoints() { return referralFlatPoints; }
    public void setReferralFlatPoints(int v) { this.referralFlatPoints = v; }
    public int getReferralWindowDays() { return referralWindowDays; }
    public void setReferralWindowDays(int v) { this.referralWindowDays = v; }
}
