package com.ecommerce.app.module.order;

import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;

public class Order {
    private UUID orderId;
    private UUID userId;
    private OrderStatus status;
    private double totalAmount;
    private double shippingFee;
    private String shippingAddress;
    private String phone;
    private Timestamp orderedAt;
    private Timestamp updatedAt;
    private List<OrderItem> orderItems;
    // Transient fields for the WhatsApp pivot: populated on checkout response
    // so the frontend can render a 'Send order to shop on WhatsApp' button
    // without a second round-trip. NULL on plain order fetches.
    private String whatsappMessage;
    private String whatsappLink;
    
    public UUID getOrderId() {
        return orderId;
    }
    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
    public OrderStatus getStatus() {
        return status;
    }
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    public double getTotalAmount() {
        return totalAmount;
    }
    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }
    public double getShippingFee() {
        return shippingFee;
    }
    public void setShippingFee(double shippingFee) {
        this.shippingFee = shippingFee;
    }
    public String getShippingAddress() {
        return shippingAddress;
    }
    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public Timestamp getorderedAt() {
        return orderedAt;
    }
    public void setorderedAt(Timestamp orderedAt) {
        this.orderedAt = orderedAt;
    }
    public Timestamp getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
    public List<OrderItem> getOrderItems() {
        return orderItems;
    }
    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }
    public String getWhatsappMessage() {
        return whatsappMessage;
    }
    public void setWhatsappMessage(String whatsappMessage) {
        this.whatsappMessage = whatsappMessage;
    }
    public String getWhatsappLink() {
        return whatsappLink;
    }
    public void setWhatsappLink(String whatsappLink) {
        this.whatsappLink = whatsappLink;
    }
}
