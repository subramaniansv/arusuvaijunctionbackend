package com.ecommerce.app.module.affiliate;

import com.ecommerce.app.config.DBConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Reads/writes the affiliate rate columns on the shared loyalty_settings
 * singleton (id = 1). Columns are added by {@link AffiliateMigration}; this
 * repository reads defensively so it works even if the migration hasn't run
 * yet (falls back to zeros / 15-day window).
 */
public class AffiliateSettingsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AffiliateSettingsRepository.class);

    public AffiliateSettings get() {
        AffiliateSettings s = new AffiliateSettings();
        String sql = "SELECT order_point_rate, referral_point_rate, referral_flat_points, "
                + "referral_window_days FROM loyalty_settings WHERE id = 1";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                BigDecimal opr = rs.getBigDecimal("order_point_rate");
                BigDecimal rpr = rs.getBigDecimal("referral_point_rate");
                s.setOrderPointRate(opr != null ? opr : BigDecimal.ZERO);
                s.setReferralPointRate(rpr != null ? rpr : BigDecimal.ZERO);
                s.setReferralFlatPoints(rs.getInt("referral_flat_points"));
                int w = rs.getInt("referral_window_days");
                s.setReferralWindowDays(w > 0 ? w : 15);
            }
        } catch (SQLException e) {
            LOG.warn("getAffiliateSettings failed (using defaults): {}", e.getMessage());
        }
        return s;
    }

    public boolean update(AffiliateSettings s, UUID adminId) {
        // Upsert the singleton; keep point_value_inr untouched via COALESCE on insert.
        String sql = """
                INSERT INTO loyalty_settings
                    (id, order_point_rate, referral_point_rate, referral_flat_points, referral_window_days, updated_by, updated_at)
                VALUES (1, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO UPDATE SET
                    order_point_rate     = EXCLUDED.order_point_rate,
                    referral_point_rate  = EXCLUDED.referral_point_rate,
                    referral_flat_points = EXCLUDED.referral_flat_points,
                    referral_window_days = EXCLUDED.referral_window_days,
                    updated_by           = EXCLUDED.updated_by,
                    updated_at           = CURRENT_TIMESTAMP
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, s.getOrderPointRate());
            ps.setBigDecimal(2, s.getReferralPointRate());
            ps.setInt(3, s.getReferralFlatPoints());
            ps.setInt(4, s.getReferralWindowDays());
            ps.setObject(5, adminId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.error("sql exception at updateAffiliateSettings ", e);
        }
        return false;
    }
}
