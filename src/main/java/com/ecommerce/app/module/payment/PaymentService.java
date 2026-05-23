package com.ecommerce.app.module.payment;

import com.ecommerce.app.config.DBConfig;
import com.ecommerce.app.module.cart.Cart;
import com.ecommerce.app.module.cart.CartItem;
import com.ecommerce.app.module.cart.CartItemRepository;
import com.ecommerce.app.module.cart.CartRepository;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.repository.EmailVerificationRepository;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;
import com.ecommerce.app.module.order.*;
import com.ecommerce.app.module.product.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * Razorpay two-phase payment flow.
 *
 * <h3>Phase 1 - initiate</h3>
 * Server-side computes the cart/buy-now total from CURRENT product data
 * (never trusts the client), creates an internal {@link Order} with
 * status {@link OrderStatus#PAYMENT_PENDING}, creates Razorpay's order via
 * the public API, and records a {@link PaymentTransaction} row in CREATED
 * state. Stock is checked for availability but NOT decremented yet.
 *
 * <h3>Phase 2 - verify</h3>
 * After the customer completes the Razorpay popup, the frontend POSTs
 * {razorpay_order_id, razorpay_payment_id, razorpay_signature} back to us.
 * We HMAC-verify the signature against our key secret, then in a single DB
 * transaction:
 *   1. re-check stock (Approach B - someone else may have bought concurrently)
 *   2. decrement stock per order item
 *   3. flip order status to PAID
 *   4. mark the PaymentTransaction CAPTURED
 *   5. clear the user's cart (only if paymentType=CART)
 * If anything fails the order goes to PAYMENT_FAILED and the caller is told
 * to contact support for a refund.
 *
 * <h3>Webhook (out-of-band, recommended)</h3>
 * Razorpay also POSTs server-to-server to /api/payment?action=webhook with
 * the SAME signature scheme but a different secret (webhook secret). This
 * is the source of truth - if our verify call was missed (network blip,
 * tab closed) the webhook will still finalize the order. Idempotent.
 *
 * <h3>Security</h3>
 * - Amount comes from our DB, never the request body.
 * - Signature verification uses constant-time compare.
 * - Email-verified users only (mirrors checkout()).
 * - Payment success WITHOUT a valid signature is rejected even if Razorpay
 *   apparently confirmed it - we must independently verify.
 */
public class PaymentService {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrderRepository orderRepository = new OrderRepository();
    private final OrderItemRepository itemRepository = new OrderItemRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final ProductVariantRepository productVariantRepository = new ProductVariantRepository();
    private final CartRepository cartRepository = new CartRepository();
    private final CartItemRepository cartItemRepository = new CartItemRepository();
    private final UserRepository userRepository = new UserRepository();
    private final EmailVerificationRepository emailVerificationRepository = new EmailVerificationRepository();
    private final PaymentTransactionRepository paymentTxnRepository = new PaymentTransactionRepository();
    private final RazorpayClient razorpay = RazorpayClient.get();

    /** What the frontend needs to open the Razorpay popup. */
    public static final class InitiationResponse {
        public final UUID orderId;
        public final String razorpayOrderId;
        public final long amountInPaise;
        public final String currency;
        public final String keyId;
        public InitiationResponse(UUID orderId, String rpOrderId, long amount, String currency, String keyId) {
            this.orderId = orderId;
            this.razorpayOrderId = rpOrderId;
            this.amountInPaise = amount;
            this.currency = currency;
            this.keyId = keyId;
        }
    }

    // ------------------------------------------------------------------
    // Phase 1 - initiate
    // ------------------------------------------------------------------

    /** Initiate payment for the user's current cart. */
    public InitiationResponse initiateCartPayment(UUID userId, String shippingAddress, String phone) {
        validateShipping(shippingAddress, phone);
        requireVerifiedEmail(userId);

        Cart cart = cartRepository.findByUserId(userId);
        if (cart == null || cart.getCartId() == null) {
            throw new RuntimeException("cart is empty");
        }
        List<CartItem> cartItems = cartItemRepository.findByCartIdWithProductDetails(cart.getCartId());
        if (cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("cart is empty");
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            Order order = new Order();
            order.setUserId(userId);
            order.setShippingAddress(shippingAddress);
            order.setPhone(phone);
            Order created = orderRepository.create(connection, order);
            if (created == null || created.getOrderId() == null) {
                connection.rollback();
                throw new RuntimeException("could not create order");
            }
            order.setOrderId(created.getOrderId());

            double total = 0.0;
            for (CartItem ci : cartItems) {
                Product product = productRepository.findById(ci.getProductId());
                if (product == null || product.getId() == null || !product.isActive()) {
                    connection.rollback();
                    throw new RuntimeException("product no longer available");
                }
                ProductVariant variant = null;
                double linePrice = product.getPrice();
                int availableStock = product.getStockQuantity();
                String variantLabel = null;
                if (ci.getVariantId() != null) {
                    variant = productVariantRepository.findById(ci.getVariantId());
                    if (variant == null || !variant.isActive()
                            || !variant.getProductId().equals(product.getId())) {
                        connection.rollback();
                        throw new RuntimeException(
                                "variant for '" + product.getName() + "' is no longer available");
                    }
                    linePrice = variant.getPrice();
                    availableStock = variant.getStockQuantity();
                    variantLabel = variant.getLabel();
                }
                if (availableStock < ci.getQuantity()) {
                    connection.rollback();
                    throw new RuntimeException("insufficient stock for '" + product.getName() + "'");
                }

                OrderItem oi = new OrderItem();
                oi.setOrderId(order.getOrderId());
                oi.setProductId(product.getId());
                oi.setVariantId(variant != null ? variant.getVariantId() : null);
                oi.setVariantLabel(variantLabel);
                oi.setQuantity(ci.getQuantity());
                oi.setPrice(linePrice);
                OrderItem createdItem = itemRepository.create(connection, oi);
                if (createdItem == null || createdItem.getOrderItemId() == null) {
                    connection.rollback();
                    throw new RuntimeException("could not persist order item");
                }
                total += linePrice * ci.getQuantity();
            }

            orderRepository.updatePrice(connection, total, order.getOrderId());
            orderRepository.updateStatus(connection, OrderStatus.PAYMENT_PENDING, order.getOrderId());

            // Hand off to Razorpay. If this throws we roll back the whole
            // initiation so we don't leave dangling PAYMENT_PENDING orders.
            long amountInPaise = toPaise(total);
            String rpOrderId = razorpay.createOrder(amountInPaise, "INR", order.getOrderId().toString());

            PaymentTransaction t = new PaymentTransaction();
            t.setOrderId(order.getOrderId());
            t.setUserId(userId);
            t.setPaymentType("CART");
            t.setRazorpayOrderId(rpOrderId);
            t.setAmount(BigDecimal.valueOf(total));
            t.setCurrency("INR");
            PaymentTransaction saved = paymentTxnRepository.create(connection, t);
            if (saved == null) {
                connection.rollback();
                throw new RuntimeException("could not record payment transaction");
            }

            connection.commit();
            return new InitiationResponse(order.getOrderId(), rpOrderId, amountInPaise, "INR", razorpay.getKeyId());
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at initiateCartPayment", e);
            throw new RuntimeException("could not initiate payment");
        } finally {
            closeQuietly(connection);
        }
    }

    /** Initiate payment for a one-shot Buy-Now (no cart involvement). */
    public InitiationResponse initiateBuyNowPayment(
            UUID userId, UUID productId, UUID variantId, int quantity,
            String shippingAddress, String phone) {
        validateShipping(shippingAddress, phone);
        if (productId == null) throw new RuntimeException("productId is required");
        if (quantity <= 0) throw new RuntimeException("quantity must be positive");
        requireVerifiedEmail(userId);

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            Product product = productRepository.findById(productId);
            if (product == null || product.getId() == null || !product.isActive()) {
                connection.rollback();
                throw new RuntimeException("product no longer available");
            }
            ProductVariant variant = null;
            double linePrice = product.getPrice();
            int availableStock = product.getStockQuantity();
            String variantLabel = null;
            if (variantId != null) {
                variant = productVariantRepository.findById(variantId);
                if (variant == null || !variant.isActive()
                        || !variant.getProductId().equals(product.getId())) {
                    connection.rollback();
                    throw new RuntimeException("variant not available");
                }
                linePrice = variant.getPrice();
                availableStock = variant.getStockQuantity();
                variantLabel = variant.getLabel();
            }
            if (availableStock < quantity) {
                connection.rollback();
                throw new RuntimeException("insufficient stock for '" + product.getName() + "'");
            }

            Order order = new Order();
            order.setUserId(userId);
            order.setShippingAddress(shippingAddress);
            order.setPhone(phone);
            Order created = orderRepository.create(connection, order);
            if (created == null || created.getOrderId() == null) {
                connection.rollback();
                throw new RuntimeException("could not create order");
            }
            order.setOrderId(created.getOrderId());

            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getOrderId());
            oi.setProductId(product.getId());
            oi.setVariantId(variant != null ? variant.getVariantId() : null);
            oi.setVariantLabel(variantLabel);
            oi.setQuantity(quantity);
            oi.setPrice(linePrice);
            OrderItem createdItem = itemRepository.create(connection, oi);
            if (createdItem == null || createdItem.getOrderItemId() == null) {
                connection.rollback();
                throw new RuntimeException("could not persist order item");
            }

            double total = linePrice * quantity;
            orderRepository.updatePrice(connection, total, order.getOrderId());
            orderRepository.updateStatus(connection, OrderStatus.PAYMENT_PENDING, order.getOrderId());

            long amountInPaise = toPaise(total);
            String rpOrderId = razorpay.createOrder(amountInPaise, "INR", order.getOrderId().toString());

            PaymentTransaction t = new PaymentTransaction();
            t.setOrderId(order.getOrderId());
            t.setUserId(userId);
            t.setPaymentType("BUY_NOW");
            t.setRazorpayOrderId(rpOrderId);
            t.setAmount(BigDecimal.valueOf(total));
            t.setCurrency("INR");
            PaymentTransaction saved = paymentTxnRepository.create(connection, t);
            if (saved == null) {
                connection.rollback();
                throw new RuntimeException("could not record payment transaction");
            }

            connection.commit();
            return new InitiationResponse(order.getOrderId(), rpOrderId, amountInPaise, "INR", razorpay.getKeyId());
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at initiateBuyNowPayment", e);
            throw new RuntimeException("could not initiate payment");
        } finally {
            closeQuietly(connection);
        }
    }

    // ------------------------------------------------------------------
    // Phase 2 - verify
    // ------------------------------------------------------------------

    /**
     * Verify Razorpay's success callback and finalize the order.
     * Idempotent: re-verifying an already-PAID order is a no-op success.
     */
    public Order verifyAndCompletePayment(UUID userId, UUID orderId,
                                          String razorpayOrderId,
                                          String razorpayPaymentId,
                                          String razorpaySignature) {
        if (orderId == null) throw new RuntimeException("orderId is required");
        if (razorpayOrderId == null || razorpayOrderId.isBlank()
                || razorpayPaymentId == null || razorpayPaymentId.isBlank()
                || razorpaySignature == null || razorpaySignature.isBlank()) {
            throw new RuntimeException("missing razorpay payment fields");
        }

        PaymentTransaction txn = paymentTxnRepository.findLatestByOrderId(orderId);
        if (txn == null) throw new RuntimeException("payment transaction not found");
        if (!Objects.equals(txn.getUserId(), userId)) {
            throw new RuntimeException("order does not belong to user");
        }
        if (!Objects.equals(txn.getRazorpayOrderId(), razorpayOrderId)) {
            throw new RuntimeException("razorpay order mismatch");
        }
        // Idempotent short-circuit.
        if ("CAPTURED".equals(txn.getPaymentStatus())) {
            return orderRepository.findByOrderID(userId, orderId);
        }

        // ALWAYS verify signature server-side. Never trust a frontend
        // "success" callback without HMAC validation.
        if (!razorpay.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            paymentTxnRepository.markFailed(txn.getPaymentTransactionId(), "signature verification failed");
            try (Connection c = DBConfig.getConnection()) {
                orderRepository.updateStatus(c, OrderStatus.PAYMENT_FAILED, orderId);
            } catch (SQLException ignored) {
            }
            throw new RuntimeException("invalid payment signature");
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            OrderStatus current = orderRepository.findStatus(connection, orderId);
            if (current == OrderStatus.PAID) {
                connection.commit();
                return orderRepository.findByOrderID(userId, orderId);
            }
            if (current != OrderStatus.PAYMENT_PENDING) {
                connection.rollback();
                throw new RuntimeException("order is not awaiting payment");
            }

            // Approach B: recheck stock now that money has been captured. If
            // anything is out of stock, the customer needs a refund.
            List<OrderItem> items = itemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                int avail;
                if (item.getVariantId() != null) {
                    ProductVariant v = productVariantRepository.findById(item.getVariantId());
                    if (v == null || !v.isActive()) {
                        markFailedAndRollback(connection, orderId, txn, "variant unavailable post-payment");
                        throw new RuntimeException(
                                "payment captured but item is no longer available - support will issue a refund");
                    }
                    avail = v.getStockQuantity();
                } else {
                    Product p = productRepository.findById(item.getProductId());
                    if (p == null || !p.isActive()) {
                        markFailedAndRollback(connection, orderId, txn, "product unavailable post-payment");
                        throw new RuntimeException(
                                "payment captured but item is no longer available - support will issue a refund");
                    }
                    avail = p.getStockQuantity();
                }
                if (avail < item.getQuantity()) {
                    markFailedAndRollback(connection, orderId, txn, "insufficient stock post-payment");
                    throw new RuntimeException(
                            "payment captured but stock ran out - support will issue a refund");
                }
            }
            // Stock looked OK above; decrement now in the same transaction.
            for (OrderItem item : items) {
                boolean ok = (item.getVariantId() != null)
                        ? productVariantRepository.decrementStock(connection, item.getVariantId(), item.getQuantity())
                        : productRepository.decrementStock(connection, item.getProductId(), item.getQuantity());
                if (!ok) {
                    markFailedAndRollback(connection, orderId, txn, "stock decrement failed post-payment");
                    throw new RuntimeException(
                            "payment captured but inventory update failed - support will issue a refund");
                }
            }

            if (!orderRepository.updateStatus(connection, OrderStatus.PAID, orderId)) {
                connection.rollback();
                throw new RuntimeException("could not update order status");
            }
            if (!paymentTxnRepository.markCaptured(connection,
                    txn.getPaymentTransactionId(), razorpayPaymentId, razorpaySignature)) {
                connection.rollback();
                throw new RuntimeException("could not update payment transaction");
            }

            // Clear cart for CART-type payments. BUY_NOW never touches cart.
            if ("CART".equalsIgnoreCase(txn.getPaymentType())) {
                Cart cart = cartRepository.findByUserId(userId);
                if (cart != null && cart.getCartId() != null) {
                    cartItemRepository.deleteByCartId(connection, cart.getCartId());
                    cartRepository.updateTotal(connection, cart.getCartId(), 0.0);
                }
            }

            connection.commit();
            Order persisted = orderRepository.findByOrderID(userId, orderId);
            if (persisted != null) sendOrderConfirmation(userId, persisted);
            return persisted;
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at verifyAndCompletePayment", e);
            throw new RuntimeException("could not finalize payment");
        } finally {
            closeQuietly(connection);
        }
    }

    // ------------------------------------------------------------------
    // Webhook (Razorpay -> us, server-to-server)
    // ------------------------------------------------------------------

    /**
     * Handle a Razorpay webhook. The body and signature header are passed
     * through from the servlet exactly as received - never re-serialize
     * the JSON, the signature is computed over raw bytes.
     *
     * Recognized events:
     *   payment.captured  - ensure order PAID + txn CAPTURED (idempotent)
     *   payment.failed    - mark PAYMENT_FAILED if still pending
     *
     * Throws on signature failure so the servlet returns 4xx and Razorpay
     * retries the delivery.
     */
    public void handleWebhook(String rawBody, String signature) {
        if (!razorpay.verifyWebhookSignature(rawBody, signature)) {
            throw new RuntimeException("invalid webhook signature");
        }
        try {
            JsonNode root = MAPPER.readTree(rawBody);
            String event = root.path("event").asText("");
            JsonNode entity = root.path("payload").path("payment").path("entity");
            String rpOrderId = entity.path("order_id").asText(null);
            String rpPaymentId = entity.path("id").asText(null);
            if (rpOrderId == null || rpOrderId.isBlank()) {
                LOG.warn("webhook missing razorpay order_id event={}", event);
                return;
            }
            PaymentTransaction txn = paymentTxnRepository.findByRazorpayOrderId(rpOrderId);
            if (txn == null) {
                LOG.warn("webhook for unknown razorpay order {}", rpOrderId);
                return;
            }

            if ("payment.captured".equals(event)) {
                // Idempotent finalize. We don't have a signature here (webhook
                // signature covers the body, not order|payment), so we trust
                // the verified webhook + the dashboard match.
                if (!"CAPTURED".equals(txn.getPaymentStatus())) {
                    Connection c = null;
                    try {
                        c = DBConfig.getConnection();
                        c.setAutoCommit(false);
                        OrderStatus current = orderRepository.findStatus(c, txn.getOrderId());
                        if (current == OrderStatus.PAYMENT_PENDING) {
                            // Best-effort - the verify endpoint should normally have done this.
                            orderRepository.updateStatus(c, OrderStatus.PAID, txn.getOrderId());
                            paymentTxnRepository.markCaptured(c, txn.getPaymentTransactionId(),
                                    rpPaymentId, "webhook");
                            c.commit();
                            LOG.info("order {} finalized via webhook", txn.getOrderId());
                        } else {
                            c.commit();
                        }
                    } catch (SQLException e) {
                        safeRollback(c);
                        LOG.error("sql at webhook payment.captured", e);
                    } finally {
                        closeQuietly(c);
                    }
                }
            } else if ("payment.failed".equals(event)) {
                if (!"CAPTURED".equals(txn.getPaymentStatus())) {
                    paymentTxnRepository.markFailed(txn.getPaymentTransactionId(), "webhook payment.failed");
                    try (Connection c = DBConfig.getConnection()) {
                        OrderStatus current = orderRepository.findStatus(c, txn.getOrderId());
                        if (current == OrderStatus.PAYMENT_PENDING) {
                            orderRepository.updateStatus(c, OrderStatus.PAYMENT_FAILED, txn.getOrderId());
                        }
                    } catch (SQLException e) {
                        LOG.error("sql at webhook payment.failed", e);
                    }
                }
            } else {
                LOG.debug("ignoring webhook event {}", event);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("error processing webhook", e);
            throw new RuntimeException("webhook processing failed");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void markFailedAndRollback(Connection c, UUID orderId, PaymentTransaction txn, String reason) {
        try { c.rollback(); } catch (Exception ignored) {}
        paymentTxnRepository.markFailed(txn.getPaymentTransactionId(), reason);
        try (Connection c2 = DBConfig.getConnection()) {
            orderRepository.updateStatus(c2, OrderStatus.PAYMENT_FAILED, orderId);
        } catch (SQLException ignored) {}
    }

    private void sendOrderConfirmation(UUID userId, Order order) {
        try {
            if (userId == null || order == null) return;
            User user = userRepository.getUser(userId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            String orderRef = order.getOrderId() != null
                    ? order.getOrderId().toString().substring(0, 8).toUpperCase()
                    : "";
            MailService.get().send(
                    user.getEmail(),
                    "Your Arusuvai order #" + orderRef + " is confirmed",
                    MailTemplates.orderPlaced(user.getFirstName(), order));
        } catch (Exception e) {
            LOG.warn("could not send order confirmation email: {}", e.getMessage());
        }
    }

    private void requireVerifiedEmail(UUID userId) {
        if (userId == null) throw new RuntimeException("unauthenticated");
        if (!emailVerificationRepository.isUserVerified(userId)) {
            throw new RuntimeException("please verify your email before placing an order");
        }
    }

    private void validateShipping(String shippingAddress, String phone) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new RuntimeException("shipping address is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("phone is required");
        }
    }

    private static long toPaise(double amount) {
        // Use BigDecimal to avoid double rounding silliness on .995 etc.
        return BigDecimal.valueOf(amount).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private void safeRollback(Connection c) {
        if (c == null) return;
        try { c.rollback(); } catch (Exception e) { LOG.error("rollback failed", e); }
    }
    private void closeQuietly(Connection c) {
        if (c == null) return;
        try { c.setAutoCommit(true); } catch (Exception ignored) {}
        try { c.close(); } catch (Exception e) { LOG.error("close failed", e); }
    }
}
