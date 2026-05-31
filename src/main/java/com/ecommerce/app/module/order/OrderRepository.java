package com.ecommerce.app.module.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ecommerce.app.config.DBConfig;

public class OrderRepository {
    private static final Logger LOG = LoggerFactory.getLogger(OrderRepository.class);

    public Order create(Connection connection, Order order) {
        String sql = "insert into orders (order_id,user_id,shipping_address,phone_number,shipping_fee) values (?,?,?,?,?)";
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(sql);
            UUID id = UUID.randomUUID();
            preparedStatement.setObject(1, id);
            preparedStatement.setObject(2, order.getUserId());
            preparedStatement.setString(3, order.getShippingAddress());
            preparedStatement.setString(4, order.getPhone());
            preparedStatement.setDouble(5, order.getShippingFee());
            preparedStatement.executeUpdate();
            order.setOrderId(id);
        } catch (SQLException e) {
            LOG.error("sql exception at create order ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at create order ", e);
        }
        return order;
    }

    public boolean updateStatus(Connection connection, OrderStatus status, UUID orderID) {
        String sql = "update orders set order_status = ? where order_id =?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setString(1, status.name());
            ps.setObject(2, orderID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateStatus order ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateStatus order ", e);
        }
        return false;
    }
        public boolean updatePrice(Connection connection, double price, UUID orderID) {
        String sql = "update orders set total_amount = ? where order_id =?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setDouble(1, price);
            ps.setObject(2, orderID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateStatus order ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateStatus order ", e);
        }
        return false;
    }

    public boolean updateShippingFee(Connection connection, double shippingFee, UUID orderID) {
        String sql = "update orders set shipping_fee = ? where order_id = ?";
        try {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setDouble(1, shippingFee);
            ps.setObject(2, orderID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateShippingFee ", e);
        } catch (Exception e) {
            LOG.error("unhandled exception at updateShippingFee ", e);
        }
        return false;
    }

    public List<Order> findByUserId(UUID userId, int limit, int offset) {
        String sql = """
                SELECT
                    o.order_id,
                    o.user_id,
                    o.order_status,
                    o.total_amount,
                    o.shipping_fee,
                    o.shipping_address,
                    o.phone_number,
                    o.ordered_at,
                    o.tracking_number,
                    o.shipping_provider,

                    oi.order_item_id,
                    oi.product_id,
                    oi.variant_id,
                    oi.variant_label,
                    oi.quantity,
                    oi.price,
                    p.name AS product_name,
                    (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.product_id AND pi.is_primary = true
                        LIMIT 1) AS image_url

                FROM orders o

                LEFT JOIN order_items oi
                ON o.order_id = oi.order_id
                LEFT JOIN products p
                ON oi.product_id = p.product_id

                WHERE o.user_id = ?

                ORDER BY o.ordered_at DESC

                LIMIT ? OFFSET ?
                """;

        Map<UUID, Order> orderMap = new LinkedHashMap<>();

        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID orderId = rs.getObject("order_id", java.util.UUID.class);
                Order order = orderMap.get(orderId);
                // Create order once
                if (order == null) {
                    order = new Order();
                    order.setOrderId(orderId);
                    order.setUserId(rs.getObject("user_id", java.util.UUID.class));
                    order.setStatus(OrderStatus.valueOf(rs.getString("order_status")));
                    order.setTotalAmount(rs.getDouble("total_amount"));
                    order.setShippingFee(rs.getDouble("shipping_fee"));
                    order.setShippingAddress(rs.getString("shipping_address"));
                    order.setPhone(rs.getString("phone_number"));
                    order.setorderedAt(rs.getTimestamp("ordered_at"));
                    order.setTrackingNumber(rs.getString("tracking_number"));
                    order.setShippingProvider(rs.getString("shipping_provider"));
                    order.setOrderItems(new ArrayList<>());
                    orderMap.put(orderId, order);
                }

                // Add order item
                if (rs.getBytes("order_item_id") != null) {
                    OrderItem item = new OrderItem();
                    item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                    item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                    item.setVariantLabel(rs.getString("variant_label"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    item.setProductName(rs.getString("product_name"));
                    item.setImageUrl(rs.getString("image_url"));
                    order.getOrderItems().add(item);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByUserId  ", e);
        } catch (Exception e) {
            LOG.error("exception at findByUserId  ", e);
        }
        return new ArrayList<>(orderMap.values());
    }

    public List<Order> findAll(int limit, int offset) {
        String sql = """
                SELECT
                    o.order_id,
                    o.user_id,
                    o.order_status,
                    o.total_amount,
                    o.shipping_fee,
                    o.shipping_address,
                    o.phone_number,
                    o.ordered_at,
                    o.tracking_number,
                    o.shipping_provider,

                    oi.order_item_id,
                    oi.product_id,
                    oi.variant_id,
                    oi.variant_label,
                    oi.quantity,
                    oi.price,
                    p.name AS product_name,
                    (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.product_id AND pi.is_primary = true
                        LIMIT 1) AS image_url

                FROM orders o

                LEFT JOIN order_items oi
                ON o.order_id = oi.order_id
                LEFT JOIN products p
                ON oi.product_id = p.product_id
                ORDER BY o.ordered_at DESC
                LIMIT ? OFFSET ?
                """;

        Map<UUID, Order> orderMap = new LinkedHashMap<>();

        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID orderId = rs.getObject("order_id", java.util.UUID.class);
                Order order = orderMap.get(orderId);
                // Create order once
                if (order == null) {
                    order = new Order();
                    order.setOrderId(orderId);
                    order.setUserId(rs.getObject("user_id", java.util.UUID.class));
                    order.setStatus(OrderStatus.valueOf(rs.getString("order_status")));
                    order.setTotalAmount(rs.getDouble("total_amount"));
                    order.setShippingFee(rs.getDouble("shipping_fee"));
                    order.setShippingAddress(rs.getString("shipping_address"));
                    order.setPhone(rs.getString("phone_number"));
                    order.setorderedAt(rs.getTimestamp("ordered_at"));
                    order.setTrackingNumber(rs.getString("tracking_number"));
                    order.setShippingProvider(rs.getString("shipping_provider"));
                    order.setOrderItems(new ArrayList<>());
                    orderMap.put(orderId, order);
                }

                // Add order item
                if (rs.getBytes("order_item_id") != null) {
                    OrderItem item = new OrderItem();
                    item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                    item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                    item.setVariantLabel(rs.getString("variant_label"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    item.setProductName(rs.getString("product_name"));
                    item.setImageUrl(rs.getString("image_url"));
                    order.getOrderItems().add(item);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findall  ", e);
        } catch (Exception e) {
            LOG.error("exception at findall  ", e);
        }
        return new ArrayList<>(orderMap.values());
    }

    public Order findByOrderID(UUID userId, UUID enterorderId) {
        return findByOrderIDInternal(userId, enterorderId);
    }

    /**
     * Admin-only overload: fetch any order regardless of owner.
     * Used by the admin order-detail view.
     */
    public Order findByOrderIDAdmin(UUID orderId) {
        return findByOrderIDInternal(null, orderId);
    }

    private Order findByOrderIDInternal(UUID userId, UUID enterorderId) {
        // NOTE: trailing whitespace inside a Java text block is stripped,
        // so "WHERE " here would lose its trailing space. We add a leading
        // space on the appended fragment to guarantee the keyword stays
        // separated from the first predicate.
        String whereUser = userId != null ? " o.user_id = ? AND" : "";
        String sql = """
                SELECT
                    o.order_id,
                    o.user_id,
                    o.order_status,
                    o.total_amount,
                    o.shipping_fee,
                    o.shipping_address,
                    o.phone_number,
                    o.ordered_at,
                    o.tracking_number,
                    o.shipping_provider,
                    oi.order_item_id,
                    oi.product_id,
                    oi.variant_id,
                    oi.variant_label,
                    oi.quantity,
                    oi.price,
                    p.name AS product_name,
                    (SELECT pi.image_url FROM product_images pi
                        WHERE pi.product_id = p.product_id AND pi.is_primary = true
                        LIMIT 1) AS image_url
                FROM orders o
                LEFT JOIN order_items oi
                ON o.order_id = oi.order_id
                LEFT JOIN products p
                ON oi.product_id = p.product_id
                WHERE""" + whereUser + " o.order_id = ?";
        Map<UUID, Order> orderMap = new LinkedHashMap<>();
        try (Connection connection = DBConfig.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            int idx = 1;
            if (userId != null) {
                ps.setObject(idx++, userId);
            }
            ps.setObject(idx, enterorderId);
   
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID orderId = rs.getObject("order_id", java.util.UUID.class);
                Order order = orderMap.get(orderId);
                // Create order once
                if (order == null) {
                    order = new Order();
                    order.setOrderId(orderId);
                    order.setUserId(rs.getObject("user_id", java.util.UUID.class));
                    order.setStatus(OrderStatus.valueOf(rs.getString("order_status")));
                    order.setTotalAmount(rs.getDouble("total_amount"));
                    order.setShippingFee(rs.getDouble("shipping_fee"));
                    order.setShippingAddress(rs.getString("shipping_address"));
                    order.setPhone(rs.getString("phone_number"));
                    order.setorderedAt(rs.getTimestamp("ordered_at"));
                    order.setTrackingNumber(rs.getString("tracking_number"));
                    order.setShippingProvider(rs.getString("shipping_provider"));
                    order.setOrderItems(new ArrayList<>());
                    orderMap.put(orderId, order);
                }

                // Add order item
                if (rs.getBytes("order_item_id") != null) {
                    OrderItem item = new OrderItem();
                    item.setProductId(rs.getObject("product_id", java.util.UUID.class));
                    item.setVariantId(rs.getObject("variant_id", java.util.UUID.class));
                    item.setVariantLabel(rs.getString("variant_label"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setPrice(rs.getDouble("price"));
                    item.setProductName(rs.getString("product_name"));
                    item.setImageUrl(rs.getString("image_url"));
                    order.getOrderItems().add(item);
                }
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByOrderID  ", e);
        } catch (Exception e) {
            LOG.error("exception at findByOrderID  ", e);
        }
        if (orderMap.isEmpty()) {
            return null;
        }
        return orderMap.values().iterator().next();
    }

    // Lightweight status lookup used by the cancel flow so the service can
    // decide whether to release stock (only cancel a previously non-cancelled
    // order returns inventory).
    public OrderStatus findStatus(Connection connection, UUID orderId) {
        String sql = "SELECT order_status FROM orders WHERE order_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return OrderStatus.valueOf(rs.getString("order_status"));
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findStatus  ", e);
        } catch (Exception e) {
            LOG.error("exception at findStatus  ", e);
        }
        return null;
    }

}
