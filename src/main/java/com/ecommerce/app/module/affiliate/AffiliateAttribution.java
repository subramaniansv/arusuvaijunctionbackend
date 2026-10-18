package com.ecommerce.app.module.affiliate;

import java.sql.Timestamp;
import java.util.UUID;

/** Binds a referred user to the link/affiliate that referred them. */
public class AffiliateAttribution {
    private UUID attributionId;
    private UUID referredUserId;
    private UUID linkId;
    private UUID affiliateId;
    private Timestamp attributedAt;
    private Timestamp expiresAt;
    private boolean consumed;

    public UUID getAttributionId() { return attributionId; }
    public void setAttributionId(UUID v) { this.attributionId = v; }
    public UUID getReferredUserId() { return referredUserId; }
    public void setReferredUserId(UUID v) { this.referredUserId = v; }
    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID v) { this.linkId = v; }
    public UUID getAffiliateId() { return affiliateId; }
    public void setAffiliateId(UUID v) { this.affiliateId = v; }
    public Timestamp getAttributedAt() { return attributedAt; }
    public void setAttributedAt(Timestamp v) { this.attributedAt = v; }
    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp v) { this.expiresAt = v; }
    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean v) { this.consumed = v; }
}
