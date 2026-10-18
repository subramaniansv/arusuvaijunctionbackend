package com.ecommerce.app.module.quick;

import com.ecommerce.app.config.DBConfig;
import com.ecommerce.app.module.delivery.DeliveryProvider;
import com.ecommerce.app.module.delivery.RapidoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Hyperlocal (quick) delivery orchestration.
 *
 * Business rules implemented:
 *  - Only ADMIN-ALLOWED pincodes are serviceable, AND the drop must fall
 *    within the store's (dynamic) service radius.
 *  - Quick products are a separate catalog flag; mixing with standard is
 *    blocked at the order layer (see checkout).
 *  - Fee is always computed server-side via the provider quote.
 *  - A rider must be confirmed available before an order is confirmed;
 *    if none is available the caller can extend ETA or ask the user.
 */
public class QuickDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(QuickDeliveryService.class);

    private final QuickStoreRepository storeRepo = new QuickStoreRepository();
    private final DeliveryProvider provider = RapidoClient.get();

    /** How long a quote is valid before it must be re-fetched (minutes). */
    private static final int QUOTE_TTL_MIN = 5;

    // ------------------------------------------------------------------
    // Serviceability
    // ------------------------------------------------------------------

    public record Serviceability(
            boolean serviceable,
            UUID storeId,
            String storeName,
            double distanceKm,
            String message
    ) {}

    /**
     * Resolve the best serving store for a drop location.
     * Gate 1: pincode must be admin-allowed for at least one active store.
     * Gate 2: drop must be within that store's current (dynamic) radius.
     * Picks the nearest qualifying store.
     */
    public Serviceability resolveStore(String dropPincode, double dropLat, double dropLng) {
        if (dropPincode == null || !dropPincode.matches("\\d{6}")) {
            return new Serviceability(false, null, null, 0, "Invalid pincode");
        }
        var candidates = storeRepo.findActiveByAllowedPincode(dropPincode);
        if (candidates.isEmpty()) {
            return new Serviceability(false, null, null, 0,
                    "Quick delivery is not available at this pincode yet");
        }
        QuickStore best = null;
        double bestDist = Double.MAX_VALUE;
        for (QuickStore s : candidates) {
            double dist = GeoUtil.haversineKm(s.getLat(), s.getLng(), dropLat, dropLng);
            if (dist <= s.getServiceRadiusKm() && dist < bestDist) {
                best = s;
                bestDist = dist;
            }
        }
        if (best == null) {
            return new Serviceability(false, null, null, 0,
                    "You are outside the quick-delivery radius for this area");
        }
        return new Serviceability(true, best.getStoreId(), best.getName(), bestDist,
                "Quick delivery available");
    }

    // ------------------------------------------------------------------
    // Quote
    // ------------------------------------------------------------------

    public record Quote(
            UUID quoteId,
            boolean serviceable,
            boolean riderAvailable,
            UUID storeId,
            double fee,
            int etaMin,
            double distanceKm,
            String message
    ) {}

    /**
     * Produce a persisted, short-TTL quote (fee + ETA + rider availability).
     * Resolves the store first, then asks the provider for a live price.
     */
    public Quote createQuote(UUID userId, String dropPincode, double dropLat, double dropLng, int weightGrams) {
        Serviceability sv = resolveStore(dropPincode, dropLat, dropLng);
        if (!sv.serviceable()) {
            return new Quote(null, false, false, null, 0, 0, sv.distanceKm(), sv.message());
        }
        QuickStore store = storeRepo.findById(sv.storeId());
        if (!provider.isConfigured()) {
            return new Quote(null, false, false, store.getStoreId(), 0, 0, sv.distanceKm(),
                    "Delivery partner not configured");
        }
        try {
            DeliveryProvider.QuoteInput in = new DeliveryProvider.QuoteInput(
                    store.getLat(), store.getLng(), store.getPincode(),
                    dropLat, dropLng, dropPincode, weightGrams);
            DeliveryProvider.QuoteResult q = provider.quote(in);
            if (!q.serviceable()) {
                return new Quote(null, false, false, store.getStoreId(), 0, 0, sv.distanceKm(),
                        q.message() != null ? q.message() : "Delivery partner cannot serve this route");
            }
            UUID quoteId = persistQuote(userId, store.getStoreId(), dropLat, dropLng, dropPincode, q);
            return new Quote(quoteId, true, q.riderAvailable(), store.getStoreId(),
                    q.fee(), q.etaMin(), q.distanceKm(),
                    q.riderAvailable() ? "ok" : "No rider available right now");
        } catch (Exception e) {
            LOG.error("createQuote failed", e);
            return new Quote(null, false, false, store.getStoreId(), 0, 0, sv.distanceKm(),
                    "Could not get a delivery quote, please retry");
        }
    }

    private UUID persistQuote(UUID userId, UUID storeId, double lat, double lng, String pincode,
                              DeliveryProvider.QuoteResult q) {
        String sql = """
                INSERT INTO delivery_quote
                   (user_id, store_id, drop_lat, drop_lng, drop_pincode, fee, eta_min,
                    distance_km, rider_available, provider_quote_id, expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                RETURNING quote_id
                """;
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, storeId);
            ps.setDouble(3, lat);
            ps.setDouble(4, lng);
            ps.setString(5, pincode);
            ps.setDouble(6, q.fee());
            ps.setInt(7, q.etaMin());
            ps.setDouble(8, q.distanceKm());
            ps.setBoolean(9, q.riderAvailable());
            ps.setString(10, q.providerQuoteId());
            ps.setTimestamp(11, Timestamp.from(Instant.now().plusSeconds(QUOTE_TTL_MIN * 60L)));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getObject("quote_id", UUID.class);
            }
        } catch (Exception e) {
            LOG.error("persistQuote failed", e);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Quote re-validation (before confirming an order)
    // ------------------------------------------------------------------

    public record ValidatedQuote(
            boolean valid,
            UUID storeId,
            double dropLat,
            double dropLng,
            String dropPincode,
            double fee,
            int etaMin,
            String providerQuoteId,
            String message
    ) {}

    /** Load a quote, ensure it belongs to the user and has not expired. */
    public ValidatedQuote loadValidQuote(UUID userId, UUID quoteId) {
        String sql = "SELECT * FROM delivery_quote WHERE quote_id = ? AND user_id = ?";
        try (Connection c = DBConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, quoteId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ValidatedQuote(false, null, 0, 0, null, 0, 0, null, "quote not found");
                }
                Timestamp exp = rs.getTimestamp("expires_at");
                if (exp != null && exp.toInstant().isBefore(Instant.now())) {
                    return new ValidatedQuote(false, null, 0, 0, null, 0, 0, null, "quote expired, please retry");
                }
                return new ValidatedQuote(true,
                        rs.getObject("store_id", UUID.class),
                        rs.getDouble("drop_lat"), rs.getDouble("drop_lng"),
                        rs.getString("drop_pincode"),
                        rs.getDouble("fee"), rs.getInt("eta_min"),
                        rs.getString("provider_quote_id"), "ok");
            }
        } catch (Exception e) {
            LOG.error("loadValidQuote failed", e);
            return new ValidatedQuote(false, null, 0, 0, null, 0, 0, null, "could not load quote");
        }
    }

    // ------------------------------------------------------------------
    // Rider availability confirmation
    // ------------------------------------------------------------------

    /**
     * Re-confirm a rider is available for this leg right now. Called just
     * before booking so we never confirm an order with no rider.
     */
    public boolean confirmRiderAvailable(UUID storeId, double dropLat, double dropLng,
                                         String dropPincode, int weightGrams) {
        QuickStore store = storeRepo.findById(storeId);
        if (store == null || !provider.isConfigured()) return false;
        try {
            DeliveryProvider.QuoteInput in = new DeliveryProvider.QuoteInput(
                    store.getLat(), store.getLng(), store.getPincode(),
                    dropLat, dropLng, dropPincode, weightGrams);
            return provider.riderAvailable(in);
        } catch (Exception e) {
            LOG.error("confirmRiderAvailable failed", e);
            return false;
        }
    }

    public QuickStoreRepository stores() { return storeRepo; }
    public DeliveryProvider provider() { return provider; }
}
