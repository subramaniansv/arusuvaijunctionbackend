package com.ecommerce.app.module.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ecommerce.app.config.DBConfig;
import com.ecommerce.app.module.product.Product;
import com.ecommerce.app.module.product.ProductRepository;
import com.ecommerce.app.module.product.ProductVariant;
import com.ecommerce.app.module.product.ProductVariantRepository;

public class CartService {
    private static final Logger LOG = LoggerFactory.getLogger(CartService.class);


    private final CartRepository cartRepository = new CartRepository();
    private final CartItemRepository cartItemRepository = new CartItemRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final ProductVariantRepository productVariantRepository = new ProductVariantRepository();

    /**
     * Fetch the current cart for the user (creates an empty one if none exists)
     * and populates items with product name, image and subtotal.
     */
    public Cart getCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId);
        if (cart == null) {
            cart = new Cart();
            cart.setUserId(userId);
            cart.setCartItems(new ArrayList<>());
            cart.setTotalAmount(0.0);
            return cart;
        }
        List<CartItem> items = cartItemRepository.findByCartIdWithProductDetails(cart.getCartId());
        cart.setCartItems(items);
        return cart;
    }

    /**
     * Add a product (optionally a specific variant) to the user's cart.
     * If the same product+variant pair is already in the cart, quantity
     * is incremented. Stock is validated against the variant when one
     * is selected, otherwise against the product itself.
     */
    public Cart addItem(UUID userId, UUID productId, UUID variantId, int quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("quantity must be greater than zero");
        }
        Product product = productRepository.findById(productId);
        if (product == null || product.getId() == null) {
            throw new RuntimeException("product not found");
        }
        if (!product.isActive()) {
            throw new RuntimeException("product is not available");
        }

        // Resolve variant up-front so we have authoritative price/label/stock
        // snapshot. The frontend sends a variantId but we never trust the
        // price it sends - we always read it from product_variants here.
        ProductVariant variant = null;
        double effectivePrice = product.getPrice();
        int effectiveStock = product.getStockQuantity();
        String variantLabel = null;
        if (variantId != null) {
            variant = productVariantRepository.findById(variantId);
            if (variant == null || !variant.isActive()) {
                throw new RuntimeException("variant not found");
            }
            if (!variant.getProductId().equals(productId)) {
                throw new RuntimeException("variant does not belong to this product");
            }
            effectivePrice = variant.getPrice();
            effectiveStock = variant.getStockQuantity();
            variantLabel = variant.getLabel();
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            Cart cart = cartRepository.findByUserId(userId);
            if (cart == null) {
                cart = new Cart();
                cart.setUserId(userId);
                cart = cartRepository.create(connection, cart);
                if (cart.getCartId() == null) {
                    connection.rollback();
                    throw new RuntimeException("could not create cart");
                }
            }

            CartItem existing = cartItemRepository.findByCartIdAndProductVariant(
                    cart.getCartId(), productId, variantId);
            int newQty = (existing == null ? 0 : existing.getQuantity()) + quantity;

            if (effectiveStock < newQty) {
                connection.rollback();
                throw new RuntimeException("requested quantity exceeds available stock");
            }

            if (existing == null) {
                CartItem item = new CartItem();
                item.setCartId(cart.getCartId());
                item.setProductId(productId);
                item.setVariantId(variantId);
                item.setVariantLabel(variantLabel);
                item.setQuantity(newQty);
                item.setPrice(effectivePrice);
                CartItem created = cartItemRepository.create(connection, item);
                if (created.getCartItemId() == null) {
                    connection.rollback();
                    throw new RuntimeException("could not add item to cart");
                }
            } else {
                boolean ok = cartItemRepository.updateQuantityAndPrice(
                        connection, existing.getCartItemId(), newQty, effectivePrice);
                if (!ok) {
                    connection.rollback();
                    throw new RuntimeException("could not update item in cart");
                }
            }

            double total = cartItemRepository.computeTotal(connection, cart.getCartId());
            cartRepository.updateTotal(connection, cart.getCartId(), total);

            connection.commit();
            return getCart(userId);
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at addItem cart service  ", e);
            throw new RuntimeException("could not add item to cart");
        } finally {
            closeQuietly(connection);
        }
    }

    /**
     * Set the absolute quantity of a (product, variant) line in the cart.
     * Pass quantity = 0 to remove the line.
     */
    public Cart updateItem(UUID userId, UUID productId, UUID variantId, int quantity) {
        if (quantity < 0) {
            throw new RuntimeException("quantity cannot be negative");
        }
        if (quantity == 0) {
            return removeItem(userId, productId, variantId);
        }

        Cart cart = cartRepository.findByUserId(userId);
        if (cart == null) {
            throw new RuntimeException("cart not found");
        }
        CartItem existing = cartItemRepository.findByCartIdAndProductVariant(
                cart.getCartId(), productId, variantId);
        if (existing == null) {
            throw new RuntimeException("item not in cart");
        }
        Product product = productRepository.findById(productId);
        if (product == null || product.getId() == null) {
            throw new RuntimeException("product not found");
        }
        double effectivePrice = product.getPrice();
        int effectiveStock = product.getStockQuantity();
        if (variantId != null) {
            ProductVariant variant = productVariantRepository.findById(variantId);
            if (variant == null || !variant.isActive()
                    || !variant.getProductId().equals(productId)) {
                throw new RuntimeException("variant not found");
            }
            effectivePrice = variant.getPrice();
            effectiveStock = variant.getStockQuantity();
        }
        if (effectiveStock < quantity) {
            throw new RuntimeException("requested quantity exceeds available stock");
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            boolean ok = cartItemRepository.updateQuantityAndPrice(
                    connection, existing.getCartItemId(), quantity, effectivePrice);
            if (!ok) {
                connection.rollback();
                throw new RuntimeException("could not update item in cart");
            }
            double total = cartItemRepository.computeTotal(connection, cart.getCartId());
            cartRepository.updateTotal(connection, cart.getCartId(), total);

            connection.commit();
            return getCart(userId);
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at updateItem cart service  ", e);
            throw new RuntimeException("could not update item in cart");
        } finally {
            closeQuietly(connection);
        }
    }

    public Cart removeItem(UUID userId, UUID productId, UUID variantId) {
        Cart cart = cartRepository.findByUserId(userId);
        if (cart == null) {
            throw new RuntimeException("cart not found");
        }
        CartItem existing = cartItemRepository.findByCartIdAndProductVariant(
                cart.getCartId(), productId, variantId);
        if (existing == null) {
            return getCart(userId);
        }

        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            boolean ok = cartItemRepository.deleteByCartItemId(connection, existing.getCartItemId());
            if (!ok) {
                connection.rollback();
                throw new RuntimeException("could not remove item from cart");
            }
            double total = cartItemRepository.computeTotal(connection, cart.getCartId());
            cartRepository.updateTotal(connection, cart.getCartId(), total);

            connection.commit();
            return getCart(userId);
        } catch (RuntimeException e) {
            safeRollback(connection);
            throw e;
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at removeItem cart service  ", e);
            throw new RuntimeException("could not remove item from cart");
        } finally {
            closeQuietly(connection);
        }
    }

    public Cart clearCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId);
        if (cart == null) {
            return getCart(userId);
        }
        Connection connection = null;
        try {
            connection = DBConfig.getConnection();
            connection.setAutoCommit(false);

            cartItemRepository.deleteByCartId(connection, cart.getCartId());
            cartRepository.updateTotal(connection, cart.getCartId(), 0.0);

            connection.commit();
            return getCart(userId);
        } catch (SQLException e) {
            safeRollback(connection);
            LOG.error("sql exception at clearCart cart service  ", e);
            throw new RuntimeException("could not clear cart");
        } finally {
            closeQuietly(connection);
        }
    }

    private void safeRollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (Exception e) {
            LOG.error("rollback failed  ", e);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (Exception e) {
            LOG.error("close connection failed  ", e);
        }
    }
}
