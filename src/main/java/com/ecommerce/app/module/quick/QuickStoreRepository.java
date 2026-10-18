package com.ecommerce.app.module.quick;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access for quick stores, admin-allowed pincodes and per-store inventory.
 */
public class QuickStoreRepository {
    private static final Logger LOG = LoggerFactory.getLogger(QuickStoreRepository.class);

    // ------------------------------------------------------------------
    // Stores
    // ------------------------------------------------------------------
    public QuickStore create(QuickStore s) {
        String sql = """
                INSERT INTO quick_store
                   (name, contact_name, contact_phone, address, lat, lng, pincode, service_radius_km, is_active)
                VALUES (?,?,?,?,?,?,?,?,?)
                RETURNING store_id
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactName());
            ps.setString(3, s.getContactPhone());
            ps.setString(4, s.getAddress());
            ps.setDouble(5, s.getLat());
            ps.setDouble(6, s.getLng());
            ps.setString(7, s.getPincode());
            ps.setDouble(8, s.getServiceRadiusKm());
            ps.setBoolean(9, s.isActive());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) s.setStoreId(rs.getObject("store_id", UUID.class));
            }
        } catch (Exception e) {
            LOG.error("create quick_store failed", e);
            return null;
        }
        return s;
    }

    public boolean updateRadius(UUID storeId, double radiusKm) {
        String sql = "UPDATE quick_store SET service_radius_km = ?, updated_at = now() WHERE store_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, radiusKm);
            ps.setObject(2, storeId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("updateRadius failed", e);
            return false;
        }
    }

    public boolean setActive(UUID storeId, boolean active) {
        String sql = "UPDATE quick_store SET is_active = ?, updated_at = now() WHERE store_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setObject(2, storeId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("setActive failed", e);
            return false;
        }
    }

    public QuickStore findById(UUID storeId) {
        String sql = "SELECT * FROM quick_store WHERE store_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (Exception e) {
            LOG.error("findById store failed", e);
        }
        return null;
    }

    public List<QuickStore> findAllActive() {
        String sql = "SELECT * FROM quick_store WHERE is_active = TRUE ORDER BY name";
        List<QuickStore> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) {
            LOG.error("findAllActive failed", e);
        }
        return out;
    }

    /**
     * Active stores whose admin-allowed pincode list contains the given pincode.
     * This is the primary serviceability gate: a pincode must be explicitly
     * whitelisted by admin before radius / distance is even considered.
     */
    public List<QuickStore> findActiveByAllowedPincode(String pincode) {
        String sql = """
                SELECT s.* FROM quick_store s
                JOIN quick_delivery_pincode p ON p.store_id = s.store_id
                WHERE s.is_active = TRUE AND p.is_active = TRUE AND p.pincode = ?
                ORDER BY s.name
                """;
        List<QuickStore> out = new ArrayList<>();
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pincode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (Exception e) {
            LOG.error("findActiveByAllowedPincode failed", e);
        }
        return out;
    }

    private QuickStore map(ResultSet rs) throws SQLException {
        QuickStore s = new QuickStore();
        s.setStoreId(rs.getObject("store_id", UUID.class));
        s.setName(rs.getString("name"));
        s.setContactName(rs.getString("contact_name"));
        s.setContactPhone(rs.getString("contact_phone"));
        s.setAddress(rs.getString("address"));
        s.setLat(rs.getDouble("lat"));
        s.setLng(rs.getDouble("lng"));
        s.setPincode(rs.getString("pincode"));
        s.setServiceRadiusKm(rs.getDouble("service_radius_km"));
        s.setActive(rs.getBoolean("is_active"));
        return s;
    }

    // ------------------------------------------------------------------
    // Allowed pincodes
    // ------------------------------------------------------------------
    public boolean addPincode(UUID storeId, String pincode) {
        String sql = """
                INSERT INTO quick_delivery_pincode (store_id, pincode)
                VALUES (?,?)
                ON CONFLICT (store_id, pincode) DO UPDATE SET is_active = TRUE
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, storeId);
            ps.setString(2, pincode);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("addPincode failed", e);
            return false;
        }
    }

    public boolean removePincode(UUID storeId, String pincode) {
        String sql = "UPDATE quick_delivery_pincode SET is_active = FALSE WHERE store_id = ? AND pincode = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, storeId);
            ps.setString(2, pincode);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("removePincode failed", e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Inventory
    // ------------------------------------------------------------------

    /** Available quantity (available - reserved) for a store/product/variant. */
    public int availableQty(Connection c, UUID storeId, UUID productId, UUID variantId) throws SQLException {
        String sql = """
                SELECT (available_qty - reserved_qty) AS free
                FROM quick_store_inventory
                WHERE store_id = ? AND product_id = ? AND is_live = TRUE
                  AND COALESCE(variant_id,'00000000-0000-0000-0000-000000000000'::uuid)
                    = COALESCE(?,'00000000-0000-0000-0000-000000000000'::uuid)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, storeId);
            ps.setObject(2, productId);
            ps.setObject(3, variantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("free");
            }
        }
        return 0;
    }

    /** Atomically reserve stock (guards against oversell). Returns true if reserved. */
    public boolean reserve(Connection c, UUID storeId, UUID productId, UUID variantId, int qty) throws SQLException {
        String sql = """
                UPDATE quick_store_inventory
                SET reserved_qty = reserved_qty + ?, updated_at = now()
                WHERE store_id = ? AND product_id = ? AND is_live = TRUE
                  AND COALESCE(variant_id,'00000000-0000-0000-0000-000000000000'::uuid)
                    = COALESCE(?,'00000000-0000-0000-0000-000000000000'::uuid)
                  AND (available_qty - reserved_qty) >= ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setObject(2, storeId);
            ps.setObject(3, productId);
            ps.setObject(4, variantId);
            ps.setInt(5, qty);
            return ps.executeUpdate() > 0;
        }
    }

    /** Commit a reservation into an actual stock reduction (on delivery booked). */
    public boolean commitReservation(Connection c, UUID storeId, UUID productId, UUID variantId, int qty) throws SQLException {
        String sql = """
                UPDATE quick_store_inventory
                SET available_qty = available_qty - ?, reserved_qty = reserved_qty - ?, updated_at = now()
                WHERE store_id = ? AND product_id = ?
                  AND COALESCE(variant_id,'00000000-0000-0000-0000-000000000000'::uuid)
                    = COALESCE(?,'00000000-0000-0000-0000-000000000000'::uuid)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, qty);
            ps.setObject(3, storeId);
            ps.setObject(4, productId);
            ps.setObject(5, variantId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Release a reservation (on cancellation / failed booking). */
    public boolean releaseReservation(Connection c, UUID storeId, UUID productId, UUID variantId, int qty) throws SQLException {
        String sql = """
                UPDATE quick_store_inventory
                SET reserved_qty = GREATEST(reserved_qty - ?, 0), updated_at = now()
                WHERE store_id = ? AND product_id = ?
                  AND COALESCE(variant_id,'00000000-0000-0000-0000-000000000000'::uuid)
                    = COALESCE(?,'00000000-0000-0000-0000-000000000000'::uuid)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setObject(2, storeId);
            ps.setObject(3, productId);
            ps.setObject(4, variantId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean upsertInventory(UUID storeId, UUID productId, UUID variantId, int availableQty) {
        String sql = """
                INSERT INTO quick_store_inventory (store_id, product_id, variant_id, available_qty)
                VALUES (?,?,?,?)
                ON CONFLICT (store_id, product_id, COALESCE(variant_id,'00000000-0000-0000-0000-000000000000'::uuid))
                DO UPDATE SET available_qty = EXCLUDED.available_qty, is_live = TRUE, updated_at = now()
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, storeId);
            ps.setObject(2, productId);
            ps.setObject(3, variantId);
            ps.setInt(4, availableQty);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOG.error("upsertInventory failed", e);
            return false;
        }
    }
}
