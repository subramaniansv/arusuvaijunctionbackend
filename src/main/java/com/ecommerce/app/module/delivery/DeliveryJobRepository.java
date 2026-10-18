package com.ecommerce.app.module.delivery;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.UUID;

/** Persistence for the provider-agnostic delivery_job table. */
public class DeliveryJobRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DeliveryJobRepository.class);

    public UUID create(UUID orderId, String provider, String providerQuoteId) {
        String sql = """
                INSERT INTO delivery_job (order_id, provider, provider_quote_id, status)
                VALUES (?,?,?, 'CREATED')
                ON CONFLICT (order_id) DO UPDATE SET provider_quote_id = EXCLUDED.provider_quote_id,
                                                     updated_at = now()
                RETURNING delivery_job_id
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            ps.setString(2, provider);
            ps.setString(3, providerQuoteId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getObject("delivery_job_id", UUID.class);
            }
        } catch (Exception e) {
            LOG.error("create delivery_job failed", e);
        }
        return null;
    }

    public boolean saveBooking(UUID orderId, DeliveryProvider.BookingResult b) {
        String sql = """
                UPDATE delivery_job
                SET provider_order_id = ?, status = ?, fee = ?, eta_min = ?,
                    rider_name = ?, rider_phone = ?, tracking_url = ?,
                    raw_payload = CAST(? AS JSONB), updated_at = now()
                WHERE order_id = ?
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.providerOrderId());
            ps.setString(2, b.status());
            ps.setDouble(3, b.fee());
            ps.setInt(4, b.etaMin());
            ps.setString(5, b.riderName());
            ps.setString(6, b.riderPhone());
            ps.setString(7, b.trackingUrl());
            ps.setString(8, b.rawPayload() != null ? b.rawPayload() : "{}");
            ps.setObject(9, orderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("saveBooking failed", e);
            return false;
        }
    }

    public boolean updateStatusByProviderOrderId(String providerOrderId, String status,
                                                 String riderName, String riderPhone, String rawPayload) {
        String sql = """
                UPDATE delivery_job
                SET status = ?,
                    rider_name = COALESCE(?, rider_name),
                    rider_phone = COALESCE(?, rider_phone),
                    raw_payload = CAST(? AS JSONB),
                    updated_at = now()
                WHERE provider_order_id = ?
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, riderName);
            ps.setString(3, riderPhone);
            ps.setString(4, rawPayload != null ? rawPayload : "{}");
            ps.setString(5, providerOrderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("updateStatusByProviderOrderId failed", e);
            return false;
        }
    }

    public UUID findOrderIdByProviderOrderId(String providerOrderId) {
        String sql = "SELECT order_id FROM delivery_job WHERE provider_order_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, providerOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getObject("order_id", UUID.class);
            }
        } catch (Exception e) {
            LOG.error("findOrderIdByProviderOrderId failed", e);
        }
        return null;
    }

    public String findStatusByOrderId(UUID orderId) {
        String sql = "SELECT status FROM delivery_job WHERE order_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("status");
            }
        } catch (Exception e) {
            LOG.error("findStatusByOrderId failed", e);
        }
        return null;
    }
}
