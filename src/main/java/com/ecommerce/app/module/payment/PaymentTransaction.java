package com.ecommerce.app.module.payment;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Persisted record of a Razorpay payment attempt for an internal order.
 *
 * Lifecycle:
 *   CREATED  - we asked Razorpay to create an order; user has the popup
 *   CAPTURED - Razorpay signature verified, money in our account
 *   FAILED   - signature invalid or Razorpay returned failure
 *   REFUNDED - refund processed (webhook or admin)
 *
 * One internal order_id may have multiple rows if the user retries
 * payment - each Razorpay order_id is its own row. The most recently
 * created row is the source of truth for "the current payment".
 */
public class PaymentTransaction {
    private UUID paymentTransactionId;
    private UUID orderId;
    private UUID userId;
    private String paymentType;        // CART | BUY_NOW
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    private BigDecimal amount;
    private String currency;
    private String paymentStatus;      // CREATED | CAPTURED | FAILED | REFUNDED
    private String rawPayload;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(UUID v) { this.paymentTransactionId = v; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID v) { this.userId = v; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String v) { this.paymentType = v; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String v) { this.razorpayOrderId = v; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String v) { this.razorpayPaymentId = v; }
    public String getRazorpaySignature() { return razorpaySignature; }
    public void setRazorpaySignature(String v) { this.razorpaySignature = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String v) { this.paymentStatus = v; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String v) { this.rawPayload = v; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp v) { this.createdAt = v; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp v) { this.updatedAt = v; }
}
