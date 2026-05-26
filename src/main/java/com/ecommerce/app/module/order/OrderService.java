package com.ecommerce.app.module.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ecommerce.app.config.DBConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

import com.ecommerce.app.module.cart.Cart;
import com.ecommerce.app.module.cart.CartItem;
import com.ecommerce.app.module.cart.CartItemRepository;
import com.ecommerce.app.module.cart.CartRepository;
import com.ecommerce.app.module.iam.security.AuthContext;
import com.ecommerce.app.module.iam.security.AuthUser;
import com.ecommerce.app.module.iam.models.User;
import com.ecommerce.app.module.iam.repository.EmailVerificationRepository;
import com.ecommerce.app.module.iam.repository.UserRepository;
import com.ecommerce.app.module.mail.MailService;
import com.ecommerce.app.module.mail.MailTemplates;
import com.ecommerce.app.module.product.*;
import com.ecommerce.app.module.payment.ShippingCalculator;
import java.util.*;
public class OrderService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

          OrderRepository orderRepository = new OrderRepository();
        OrderItemRepository itemRepository = new OrderItemRepository();
        ProductRepository productRepository = new ProductRepository();
        ProductVariantRepository productVariantRepository = new ProductVariantRepository();
        CartRepository cartRepository = new CartRepository();
        CartItemRepository cartItemRepository = new CartItemRepository();
        UserRepository userRepository = new UserRepository();
        EmailVerificationRepository emailVerificationRepository = new EmailVerificationRepository();

    // ------------------------------------------------------------------
    // WhatsApp pivot configuration.
    //
    // SHOP_WHATSAPP_NUMBER must be an international-format phone number with
    // country code and NO leading '+' or spaces (wa.me convention).
    // Example: 919876543210 for India +91 98765 43210.
    //
    // TODO when going live: replace with the real shop number, or read from
    // ENVConfig once secrets-to-env is wired up.
    // ------------------------------------------------------------------
    private static final String SHOP_WHATSAPP_NUMBER = "919843471463";
    private static final String CURRENCY = "Rs.";

    public Order create(Order order) throws RuntimeException {

        Connection connection = null;
        double price = 0;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);
            Order created = orderRepository.create(connection, order);
            order.setOrderId(created.getOrderId());
            if (created.getOrderId() == null) {
                LOG.info("error creation at order repo from order service");
                connection.rollback();
                return null;
            }
            order.setOrderId(created.getOrderId());
            for (OrderItem item : order.getOrderItems()) {
                // inventory check
                LOG.info("{}", (Object) created.getOrderId()+"order id set for item"+item.getProductId());

                item.setOrderId(created.getOrderId());
                Product product = productRepository.findById(item.getProductId());
                // If the caller supplied a variantId on the order item, that
                // variant's price/stock are authoritative; otherwise fall
                // back to the product-level price/stock.
                ProductVariant variant = null;
                double linePrice = product.getPrice();
                int availableStock = product.getStockQuantity();
                if (item.getVariantId() != null) {
                    variant = productVariantRepository.findById(item.getVariantId());
                    if (variant == null || !variant.isActive()
                            || !variant.getProductId().equals(product.getId())) {
                        connection.rollback();
                        throw new RuntimeException("variant not available");
                    }
                    linePrice = variant.getPrice();
                    availableStock = variant.getStockQuantity();
                    item.setVariantLabel(variant.getLabel());
                }
                if (availableStock < item.getQuantity()) {
                    connection.rollback();
                    throw new RuntimeException("stock not available");

                } else {
                    boolean isOk = (variant != null)
                            ? productVariantRepository.decrementStock(connection, variant.getVariantId(), item.getQuantity())
                            : productRepository.decrementStock(connection, product.getId(), item.getQuantity());
                    if (!isOk) {
                        LOG.info("error at decreasing the stock");
                        connection.rollback();
                        return null;
                    }
                }
                item.setPrice(linePrice);

                OrderItem createditem = itemRepository.create(connection, item);
                if (createditem.getOrderItemId() == null) {
                    LOG.info("error creation at order item repo from order service");
                    connection.rollback();
                    return null;
                } else {
                    item.setOrderItemId(createditem.getOrderItemId());
                    price += linePrice * item.getQuantity();
                }

            }
            orderRepository.updatePrice(connection, price, order.getOrderId());
            order.setStatus(OrderStatus.PENDING);
            orderRepository.updateStatus(connection, order.getStatus(), order.getOrderId());
            connection.commit();
            return orderRepository.findByOrderID(order.getUserId(), order.getOrderId());

        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("Exception of sql occured order service ", e);
            return null;
        } finally {
            closeQuietly(connection);
        }
    }

    public Order getOrderById(UUID orderId){
        AuthUser user = AuthContext.get();
        // Admins can view any order; regular users are scoped to their own.
        if (user != null && user.hasRole("admin")) {
            return orderRepository.findByOrderIDAdmin(orderId);
        }
        return orderRepository.findByOrderID(user.getUserId(), orderId);
    }
    public List<Order> getOrderByUserId(int limit,int offset){
          AuthUser user = AuthContext.get();
          return orderRepository.findByUserId(user.getUserId(), limit, offset) ;
    }

    public List<Order> getAllOrders(int limit,int offset){
        return orderRepository.findAll(limit, offset);
    }

    /**
     * Admin-only: move an order through its status enum.
     *
     * Special-case CANCELLED: when transitioning from any non-CANCELLED
     * state into CANCELLED, the order's items are released back into stock
     * in the SAME transaction as the status flip. Re-cancelling an already
     * cancelled order is a no-op (idempotent) and never double-credits stock.
     */
    public boolean updateOrderStatus(UUID orderId, OrderStatus status) {
        if (orderId == null || status == null) {
            throw new RuntimeException("orderId and status are required");
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            OrderStatus current = orderRepository.findStatus(connection, orderId);
            if (current == null) {
                connection.rollback();
                return false; // controller maps to 404
            }
            if (current == status) {
                // nothing to do
                connection.commit();
                return true;
            }

            boolean updated = orderRepository.updateStatus(connection, status, orderId);
            if (!updated) {
                connection.rollback();
                return false;
            }

            // Release inventory when an order is cancelled. Only on the FIRST
            // transition into CANCELLED — guarded by the current != status
            // check above so a re-cancel cannot double-credit stock.
            if (status == OrderStatus.CANCELLED && current != OrderStatus.CANCELLED) {
                List<OrderItem> items = itemRepository.findByOrderId(orderId);
                for (OrderItem item : items) {
                    boolean ok;
                    if (item.getVariantId() != null) {
                        ok = productVariantRepository.incrementStock(
                                connection, item.getVariantId(), item.getQuantity());
                    } else {
                        ok = productRepository.incrementStock(
                                connection, item.getProductId(), item.getQuantity());
                    }
                    if (!ok) {
                        connection.rollback();
                        throw new RuntimeException(
                                "could not release stock for product " + item.getProductId());
                    }
                }
            }

            connection.commit();
            // Fire-and-forget customer notification of the status change.
            // PENDING is the initial state set on checkout (already covered
            // by orderPlaced) so we skip it here to avoid duplicate noise.
            if (status != OrderStatus.PENDING) {
                sendOrderStatusEmail(orderId, status);
            }
            return true;
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at updateOrderStatus  ", e);
            throw new RuntimeException("could not update order status");
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Fire-and-forget email when an admin moves an order through its status
     * lifecycle (confirmed / shipped / delivered / cancelled). Never throws.
     */
    private void sendOrderStatusEmail(UUID orderId, OrderStatus status) {
        try {
            Order persisted = orderRepository.findByOrderIDAdmin(orderId);
            if (persisted == null || persisted.getUserId() == null) return;
            User user = userRepository.getUser(persisted.getUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;
            String orderRef = persisted.getOrderId() != null
                    ? persisted.getOrderId().toString().substring(0, 8).toUpperCase()
                    : "";
            MailService.get().send(
                    user.getEmail(),
                    "Arusuvai order #" + orderRef + " - " + status.name(),
                    MailTemplates.orderStatusUpdate(user.getFirstName(), persisted, status.name()));
        } catch (Exception e) {
            LOG.warn("could not send order status email: {}", e.getMessage());
        }
    }

    /**
     * Cart-based checkout. Reads the authenticated user's cart, validates stock
     * against current product state, creates an order + order_items, decrements
     * stock, and clears the cart. All inside a single JDBC transaction so any
     * failure rolls back the whole operation.
     *
     * @param userId           authenticated user (taken from AuthContext by the controller)
     * @param shippingAddress  required
     * @param phone            required
     * @return the persisted order, or null on failure
     */
    public Order checkout(UUID userId, String shippingAddress, String phone) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new RuntimeException("shipping address is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("phone is required");
        }
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

            // 1. create order header
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

            // 2. validate stock & create order_items using live product price
            double total = 0.0;
            int totalQty = 0;
            for (CartItem ci : cartItems) {
                Product product = productRepository.findById(ci.getProductId());
                if (product == null || product.getId() == null) {
                    connection.rollback();
                    throw new RuntimeException("product no longer available");
                }
                if (!product.isActive()) {
                    connection.rollback();
                    throw new RuntimeException("product '" + product.getName() + "' is not available");
                }

                // Variant resolution: if the cart line points at a variant we
                // re-read it here (do not trust cart price/label snapshots
                // for stock decisions) and snapshot the current values onto
                // the order line.
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

                boolean stockOk = (variant != null)
                        ? productVariantRepository.decrementStock(connection, variant.getVariantId(), ci.getQuantity())
                        : productRepository.decrementStock(connection, product.getId(), ci.getQuantity());
                if (!stockOk) {
                    connection.rollback();
                    throw new RuntimeException("could not decrement stock for '" + product.getName() + "'");
                }

                OrderItem oi = new OrderItem();
                oi.setOrderId(order.getOrderId());
                oi.setProductId(product.getId());
                oi.setVariantId(variant != null ? variant.getVariantId() : null);
                oi.setVariantLabel(variantLabel);
                oi.setQuantity(ci.getQuantity());
                oi.setPrice(linePrice); // snapshot live variant/product price, not cart price
                OrderItem createdItem = itemRepository.create(connection, oi);
                if (createdItem == null || createdItem.getOrderItemId() == null) {
                    connection.rollback();
                    throw new RuntimeException("could not persist order item");
                }
                total += linePrice * ci.getQuantity();
                totalQty += ci.getQuantity();
            }

            // 3. finalize order
            double computedShipping = ShippingCalculator.calculate(shippingAddress, totalQty, total);
            if (computedShipping > 0) {
                orderRepository.updateShippingFee(connection, computedShipping, order.getOrderId());
                total += computedShipping;
            }
            orderRepository.updatePrice(connection, total, order.getOrderId());
            orderRepository.updateStatus(connection, OrderStatus.PENDING, order.getOrderId());

            // 4. clear the cart
            cartItemRepository.deleteByCartId(connection, cart.getCartId());
            cartRepository.updateTotal(connection, cart.getCartId(), 0.0);

            connection.commit();
            Order persisted = orderRepository.findByOrderID(userId, order.getOrderId());
            if (persisted != null) {
                decorateWithWhatsApp(persisted);
                sendOrderConfirmation(userId, persisted);
            }
            return persisted;
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at checkout  ", e);
            throw new RuntimeException("checkout failed");
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Direct "Buy now" checkout. Bypasses the cart entirely - creates an
     * order with a single line item for the given product/variant and
     * quantity. The user's cart is NOT touched (they keep whatever they
     * had been collecting). All other behaviour (stock validation, price
     * snapshotting, WhatsApp pivot, confirmation email) matches
     * {@link #checkout}.
     */
    public Order checkoutSingle(UUID userId, UUID productId, UUID variantId,
                                int quantity, String shippingAddress, String phone) {
        if (shippingAddress == null || shippingAddress.isBlank()) {
            throw new RuntimeException("shipping address is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new RuntimeException("phone is required");
        }
        if (productId == null) {
            throw new RuntimeException("productId is required");
        }
        if (quantity <= 0) {
            throw new RuntimeException("quantity must be positive");
        }
        requireVerifiedEmail(userId);

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            // 1. resolve product + (optional) variant - same rules as cart checkout
            Product product = productRepository.findById(productId);
            if (product == null || product.getId() == null) {
                connection.rollback();
                throw new RuntimeException("product no longer available");
            }
            if (!product.isActive()) {
                connection.rollback();
                throw new RuntimeException("product '" + product.getName() + "' is not available");
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
                    throw new RuntimeException(
                            "variant for '" + product.getName() + "' is no longer available");
                }
                linePrice = variant.getPrice();
                availableStock = variant.getStockQuantity();
                variantLabel = variant.getLabel();
            }

            if (availableStock < quantity) {
                connection.rollback();
                throw new RuntimeException("insufficient stock for '" + product.getName() + "'");
            }

            // 2. create order header
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

            // 3. decrement stock
            boolean stockOk = (variant != null)
                    ? productVariantRepository.decrementStock(connection, variant.getVariantId(), quantity)
                    : productRepository.decrementStock(connection, product.getId(), quantity);
            if (!stockOk) {
                connection.rollback();
                throw new RuntimeException("could not decrement stock for '" + product.getName() + "'");
            }

            // 4. single order_item
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

            // 5. totals + status
            double merchandiseTotal = linePrice * quantity;
            double computedShipping = ShippingCalculator.calculate(shippingAddress, quantity, merchandiseTotal);
            if (computedShipping > 0) {
                orderRepository.updateShippingFee(connection, computedShipping, order.getOrderId());
            }
            double total = merchandiseTotal + computedShipping;
            orderRepository.updatePrice(connection, total, order.getOrderId());
            orderRepository.updateStatus(connection, OrderStatus.PENDING, order.getOrderId());

            // NOTE: cart is intentionally NOT cleared on Buy-Now.

            connection.commit();
            Order persisted = orderRepository.findByOrderID(userId, order.getOrderId());
            if (persisted != null) {
                decorateWithWhatsApp(persisted);
                sendOrderConfirmation(userId, persisted);
            }
            return persisted;
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at checkoutSingle  ", e);
            throw new RuntimeException("checkout failed");
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Block checkout until the user has confirmed their email address by
     * clicking the verification link we mail on registration. Throws a
     * RuntimeException that the controller maps to a 400 with this exact
     * message so the UI can prompt the user to verify (or resend the link).
     */
    private void requireVerifiedEmail(UUID userId) {
        if (userId == null) {
            throw new RuntimeException("unauthenticated");
        }
        if (!emailVerificationRepository.isUserVerified(userId)) {
            throw new RuntimeException(
                    "please verify your email before placing an order");
        }
    }

    private void safeRollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (Exception e) {
            LOG.error("rollback failed at checkout  ", e);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (Exception ignored) {
        }
        try {
            connection.close();
        } catch (Exception e) {
            LOG.error("close failed at checkout  ", e);
        }
    }

    // ------------------------------------------------------------------
    // WhatsApp message build.
    //
    // Mutates the given order to add `whatsappMessage` (human-readable
    // multi-line text) and `whatsappLink` (a wa.me URL the frontend can open
    // in a new tab; tapping it opens WhatsApp with the message pre-filled).
    //
    // Order items must already be hydrated with productName (the JOIN in
    // OrderRepository.findByOrderID does this). Items without a name fall
    // back to the product UUID so the message is never silently empty.
    // ------------------------------------------------------------------
    private void decorateWithWhatsApp(Order order) {
        String message = buildWhatsAppMessage(order);
        order.setWhatsappMessage(message);
        order.setWhatsappLink(buildWhatsAppLink(SHOP_WHATSAPP_NUMBER, message));
    }

    /**
     * Fire-and-forget order confirmation email. Looks up the user's email
     * from the IAM tables (we only have the userId on the Order). Never
     * throws - mail failures must not break checkout.
     */
    private void sendOrderConfirmation(java.util.UUID userId, Order order) {
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

    private String buildWhatsAppMessage(Order order) {
        StringBuilder sb = new StringBuilder();
        sb.append("*New order*\n");
        sb.append("Order #").append(order.getOrderId()).append('\n');
        if (order.getorderedAt() != null) {
            sb.append("Placed: ").append(order.getorderedAt()).append('\n');
        }
        sb.append('\n');
        sb.append("*Items*\n");

        List<OrderItem> items = order.getOrderItems();
        if (items != null) {
            for (OrderItem item : items) {
                String name = item.getProductName();
                if (name == null || name.isBlank()) {
                    name = String.valueOf(item.getProductId());
                }
                String label = item.getVariantLabel();
                if (label != null && !label.isBlank()) {
                    name = name + " (" + label + ")";
                }
                double lineTotal = item.getPrice() * item.getQuantity();
                sb.append("- ").append(name)
                  .append(" x").append(item.getQuantity())
                  .append("  ").append(CURRENCY).append(formatMoney(lineTotal))
                  .append('\n');
            }
        }
        sb.append('\n');
        sb.append("*Total*: ").append(CURRENCY).append(formatMoney(order.getTotalAmount())).append('\n');
        sb.append('\n');
        sb.append("*Deliver to*\n");
        if (order.getShippingAddress() != null) {
            sb.append(order.getShippingAddress()).append('\n');
        }
        if (order.getPhone() != null) {
            sb.append("Phone: ").append(order.getPhone()).append('\n');
        }
        return sb.toString();
    }

    private String buildWhatsAppLink(String shopNumber, String message) {
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "https://wa.me/" + shopNumber + "?text=" + encoded;
    }

    private String formatMoney(double value) {
        // 2 decimal places, no locale surprises (no thousands separator).
        return String.format(Locale.ROOT, "%.2f", value);
    }

}
