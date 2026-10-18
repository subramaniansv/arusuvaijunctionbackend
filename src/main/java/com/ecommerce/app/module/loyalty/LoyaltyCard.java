package com.ecommerce.app.module.loyalty;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * A user's loyalty card. Created lazily on their first check-in. The
 * {@code cardCode} is a short, human-shareable code that can be embedded in a
 * QR for that specific user; the generic store QR instead resolves to the
 * authenticated caller's card at scan time.
 */
public class LoyaltyCard {
    private UUID cardId;
    private UUID userId;
    private String cardCode;
    private int totalVisits;
    private int points;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public UUID getCardId() { return cardId; }
    public void setCardId(UUID cardId) { this.cardId = cardId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getCardCode() { return cardCode; }
    public void setCardCode(String cardCode) { this.cardCode = cardCode; }

    public int getTotalVisits() { return totalVisits; }
    public void setTotalVisits(int totalVisits) { this.totalVisits = totalVisits; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
