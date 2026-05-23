package com.ecommerce.app.module.payment;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

public class PaymentTransactionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentTransactionRepository.class);

    /** Insert a freshly-created (status=CREATED) transaction. Uses the supplied connection. */
    public PaymentTransaction create(Connection c, PaymentTransaction t) {
        UUID id = UUID.randomUUID();
        String sql =
            "INSERT INTO payment_transactions (" +
            "  payment_transaction_id, order_id, user_id, payment_type, " +
            "  razorpay_order_id, amount, currency, payment_status" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, 'CREATED')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, t.getOrderId());
            ps.setObject(3, t.getUserId());
            ps.setString(4, t.getPaymentType() == null ? "CART" : t.getPaymentType());
            ps.setString(5, t.getRazorpayOrderId());
            ps.setBigDecimal(6, t.getAmount());
            ps.setString(7, t.getCurrency() == null ? "INR" : t.getCurrency());
            int n = ps.executeUpdate();
            if (n == 1) {
                t.setPaymentTransactionId(id);
                t.setPaymentStatus("CREATED");
                return t;
            }
        } catch (SQLException e) {
            LOG.error("sql exception at PaymentTransactionRepository.create", e);
        }
        return null;
    }

    /** Look up the most recent transaction for an internal order. */
    public PaymentTransaction findLatestByOrderId(UUID orderId) {
        String sql =
            "SELECT * FROM payment_transactions " +
            "WHERE order_id = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findLatestByOrderId", e);
        }
        return null;
    }

    public PaymentTransaction findByRazorpayOrderId(String rpOrderId) {
        String sql = "SELECT * FROM payment_transactions WHERE razorpay_order_id = ? " +
                     "ORDER BY created_at DESC LIMIT 1";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rpOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOG.error("sql exception at findByRazorpayOrderId", e);
        }
        return null;
    }

    /** Mark a transaction CAPTURED with the Razorpay payment_id + signature. */
    public boolean markCaptured(Connection c, UUID id, String paymentId, String signature) {
        String sql =
            "UPDATE payment_transactions " +
            "SET payment_status='CAPTURED', razorpay_payment_id=?, razorpay_signature=?, updated_at=now() " +
            "WHERE payment_transaction_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, paymentId);
            ps.setString(2, signature);
            ps.setObject(3, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at markCaptured", e);
            return false;
        }
    }

    /** Mark a transaction FAILED. Used on signature mismatch or webhook failure. */
    public boolean markFailed(UUID id, String reason) {
        String sql =
            "UPDATE payment_transactions " +
            "SET payment_status='FAILED', raw_payload=?, updated_at=now() " +
            "WHERE payment_transaction_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setObject(2, id);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.error("sql exception at markFailed", e);
            return false;
        }
    }

    private PaymentTransaction mapRow(ResultSet rs) throws SQLException {
        PaymentTransaction t = new PaymentTransaction();
        t.setPaymentTransactionId((UUID) rs.getObject("payment_transaction_id"));
        t.setOrderId((UUID) rs.getObject("order_id"));
        t.setUserId((UUID) rs.getObject("user_id"));
        t.setPaymentType(rs.getString("payment_type"));
        t.setRazorpayOrderId(rs.getString("razorpay_order_id"));
        t.setRazorpayPaymentId(rs.getString("razorpay_payment_id"));
        t.setRazorpaySignature(rs.getString("razorpay_signature"));
        BigDecimal amt = rs.getBigDecimal("amount");
        t.setAmount(amt);
        t.setCurrency(rs.getString("currency"));
        t.setPaymentStatus(rs.getString("payment_status"));
        t.setRawPayload(rs.getString("raw_payload"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setUpdatedAt(rs.getTimestamp("updated_at"));
        return t;
    }
}
