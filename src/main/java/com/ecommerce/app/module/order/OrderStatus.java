package com.ecommerce.app.module.order;

/**
 * Order lifecycle.
 *
 * Payment-aware flow (Razorpay):
 *   PAYMENT_PENDING -> (signature verified, stock decremented) -> PAID
 *                                                              -> PAYMENT_FAILED
 *
 * Post-payment fulfilment (admin actions):
 *   PAID -> CONFIRMED -> SHIPPED -> DELIVERED
 *   any  -> CANCELLED (re-credits stock when transitioning from a paid state)
 *   PAID -> REFUNDED  (payment-side reversal; stock release is admin-driven)
 *
 * PENDING is retained for backwards compatibility with the legacy
 * cash-on-delivery / WhatsApp checkout path that never went through a
 * Razorpay payment.
 */
public enum OrderStatus {
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED
}
