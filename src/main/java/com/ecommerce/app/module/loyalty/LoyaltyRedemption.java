package com.ecommerce.app.module.loyalty;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * A single point-redemption event (points spent for cash value).
 *
 *   channel = ONLINE  -> auto-approved, applied as a checkout discount
 *           = OFFLINE -> customer request, points reserved, admin approves/rejects
 *   status  = PENDING | APPROVED | REJECTED | CANCELLED
 *
 * point_value is snapshotted at redemption time so a later rate change never
 * alters a settled spend. cash_value = points_used * point_value.
 */
public class LoyaltyRedemption {
    private UUID redemptionId;
    private UUID cardId;
    private UUID userId;
    private String channel;
    private String status;
    private int pointsUsed;
    private BigDecimal pointValue;
    private BigDecimal cashValue;
    private UUID orderId;
    private String note;
    private UUID decidedBy;
    private Timestamp decidedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Output-only enrichment for the admin feed (joined from users).
    private String userEmail;
    private String userName;

    public UUID getRedemptionId() { return redemptionId; }
    public void setRedemptionId(UUID v) { this.redemptionId = v; }

    public UUID getCardId() { return cardId; }
    public void setCardId(UUID v) { this.cardId = v; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }

    public String getChannel() { return channel; }
    public void setChannel(String v) { this.channel = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public int getPointsUsed() { return pointsUsed; }
    public void setPointsUsed(int v) { this.pointsUsed = v; }

    public BigDecimal getPointValue() { return pointValue; }
    public void setPointValue(BigDecimal v) { this.pointValue = v; }

    public BigDecimal getCashValue() { return cashValue; }
    public void setCashValue(BigDecimal v) { this.cashValue = v; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }

    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }

    public UUID getDecidedBy() { return decidedBy; }
    public void setDecidedBy(UUID v) { this.decidedBy = v; }

    public Timestamp getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Timestamp v) { this.decidedAt = v; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp v) { this.createdAt = v; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp v) { this.updatedAt = v; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String v) { this.userEmail = v; }

    public String getUserName() { return userName; }
    public void setUserName(String v) { this.userName = v; }
}
