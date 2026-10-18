package com.ecommerce.app.module.affiliate;

import java.sql.Timestamp;
import java.util.UUID;

/** A shareable referral link owned by an affiliate (a user). */
public class AffiliateLink {
    private UUID linkId;
    private String code;
    private UUID affiliateId;
    private UUID productId;      // nullable: null = generic site link
    private String targetPath;
    private int clicks;
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Output-only enrichment for the "my links" feed.
    private String shareUrl;
    private long conversions;
    private long pointsEarned;

    public UUID getLinkId() { return linkId; }
    public void setLinkId(UUID v) { this.linkId = v; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public UUID getAffiliateId() { return affiliateId; }
    public void setAffiliateId(UUID v) { this.affiliateId = v; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID v) { this.productId = v; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String v) { this.targetPath = v; }
    public int getClicks() { return clicks; }
    public void setClicks(int v) { this.clicks = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp v) { this.createdAt = v; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp v) { this.updatedAt = v; }
    public String getShareUrl() { return shareUrl; }
    public void setShareUrl(String v) { this.shareUrl = v; }
    public long getConversions() { return conversions; }
    public void setConversions(long v) { this.conversions = v; }
    public long getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(long v) { this.pointsEarned = v; }
}
