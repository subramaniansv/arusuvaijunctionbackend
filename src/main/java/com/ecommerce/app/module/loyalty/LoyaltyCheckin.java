package com.ecommerce.app.module.loyalty;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * A single loyalty visit.
 *
 *   source = QR     -> offline scan, starts PENDING, needs admin approval
 *   source = ORDER  -> online purchase, auto APPROVED for the order day
 *
 * status  = PENDING | APPROVED | REJECTED
 * visitDay= the calendar day this visit counts toward (one per user/day).
 *
 * purchaseAmount = the ₹ value the customer spent in-store for this visit.
 *                  Captured by the admin at approval time so offline visits
 *                  earn points the SAME way online orders do (order_point_rate).
 *                  Null for visits with no recorded spend (legacy/QR-only).
 */
public class LoyaltyCheckin {
    private UUID checkinId;
    private UUID cardId;
    private UUID userId;
    private String source;    // QR | ORDER
    private String status;    // PENDING | APPROVED | REJECTED
    private Date visitDay;
    private UUID orderId;     // set when source = ORDER
    private BigDecimal purchaseAmount; // in-store spend captured by admin (QR visits)
    private Integer pointsAwarded;     // points credited when this visit was approved
    private String note;
    private UUID approvedBy;
    private Timestamp approvedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Output-only join fields for the admin list (not persisted directly).
    private String userEmail;
    private String userName;

    public UUID getCheckinId() { return checkinId; }
    public void setCheckinId(UUID checkinId) { this.checkinId = checkinId; }

    public UUID getCardId() { return cardId; }
    public void setCardId(UUID cardId) { this.cardId = cardId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getVisitDay() { return visitDay; }
    public void setVisitDay(Date visitDay) { this.visitDay = visitDay; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public BigDecimal getPurchaseAmount() { return purchaseAmount; }
    public void setPurchaseAmount(BigDecimal purchaseAmount) { this.purchaseAmount = purchaseAmount; }

    public Integer getPointsAwarded() { return pointsAwarded; }
    public void setPointsAwarded(Integer pointsAwarded) { this.pointsAwarded = pointsAwarded; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Timestamp getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Timestamp approvedAt) { this.approvedAt = approvedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
}
