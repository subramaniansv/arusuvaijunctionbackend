package com.ecommerce.app.module.shipping;

import com.ecommerce.app.module.payment.ShippingCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shipping service that delegates to Delhivery for live rates/serviceability
 * and falls back to the static ShippingCalculator when Delhivery is unavailable.
 *
 * This is the single entry point for all shipping operations.
 * The static calculator in payment/ is preserved as the fallback.
 */
public class ShippingService {
    private static final Logger LOG = LoggerFactory.getLogger(ShippingService.class);

    private final DelhiveryClient delhivery = DelhiveryClient.get();

    // ------------------------------------------------------------------
    // Serviceability
    // ------------------------------------------------------------------

    public ServiceabilityResult checkServiceability(String pincode) {
        LOG.info("[SHIPPING] checkServiceability pin={} delhiveryConfigured={}", pincode, delhivery.isConfigured());
        if (!delhivery.isConfigured()) {
            LOG.warn("[SHIPPING] Delhivery not configured, assuming all pincodes serviceable");
            return new ServiceabilityResult(true, false, 5, null);
        }
        try {
            ServiceabilityResult result = delhivery.checkServiceability(pincode);
            LOG.info("[SHIPPING] Delhivery serviceability OK: {}", result);
            return result;
        } catch (Exception e) {
            LOG.error("[SHIPPING] Delhivery serviceability FAILED for pin={}, falling back", pincode, e);
            return new ServiceabilityResult(true, false, 7, null);
        }
    }

    // ------------------------------------------------------------------
    // Rate Calculation (with fallback)
    // ------------------------------------------------------------------

    /**
     * Calculate shipping rate. Tries Delhivery first; on failure, falls
     * back to the static ShippingCalculator.
     *
     * @param shippingAddress full address (used by fallback to extract pincode)
     * @param pincode         destination pincode (used by Delhivery)
     * @param weightGrams     total shipment weight
     * @param merchandiseTotal order subtotal (for free-shipping threshold)
     * @return shipping fee in INR
     */
    public double calculateRate(String shippingAddress, String pincode, int weightGrams, double merchandiseTotal) {
        LOG.info("[SHIPPING] calculateRate pin={} weight={}g subtotal={} delhiveryConfigured={}",
                pincode, weightGrams, merchandiseTotal, delhivery.isConfigured());
        // Free shipping threshold (same as static calculator)
        if (merchandiseTotal >= ShippingCalculator.FREE_ABOVE_INR) {
            LOG.info("[SHIPPING] FREE shipping (subtotal {} >= threshold {})", merchandiseTotal, ShippingCalculator.FREE_ABOVE_INR);
            return 0.0;
        }

        if (delhivery.isConfigured()) {
            try {
                RateResult rate = delhivery.calculateRate(pincode, weightGrams);
                LOG.info("[SHIPPING] Delhivery rate SUCCESS: ₹{} zone={} chargedWeight={}",
                        rate.totalAmount(), rate.zone(), rate.chargedWeightGrams());
                return rate.totalAmount();
            } catch (Exception e) {
                LOG.warn("[SHIPPING] Delhivery rate FAILED for pin={}, falling back to static calculator", pincode, e);
            }
        }

        // Fallback: static zone-based calculator
        double fallback = ShippingCalculator.calculateByGrams(shippingAddress, weightGrams, merchandiseTotal);
        LOG.info("[SHIPPING] Static calculator fallback: ₹{}", fallback);
        return fallback;
    }

    // ------------------------------------------------------------------
    // Shipment Creation
    // ------------------------------------------------------------------

    public ShipmentResult createShipment(ShipmentRequest request) throws Exception {
        if (!delhivery.isConfigured()) {
            throw new IllegalStateException("Delhivery is not configured. Set DELHIVERY_API_TOKEN in environment.");
        }
        return delhivery.createShipment(request);
    }

    // ------------------------------------------------------------------
    // Tracking
    // ------------------------------------------------------------------

    public TrackingResult trackShipment(String waybill) throws Exception {
        if (!delhivery.isConfigured()) {
            throw new IllegalStateException("Delhivery is not configured. Set DELHIVERY_API_TOKEN in environment.");
        }
        return delhivery.track(waybill);
    }

    // ------------------------------------------------------------------
    // Cancel Shipment
    // ------------------------------------------------------------------

    public boolean cancelShipment(String waybill) throws Exception {
        if (!delhivery.isConfigured()) {
            throw new IllegalStateException("Delhivery is not configured. Set DELHIVERY_API_TOKEN in environment.");
        }
        return delhivery.cancelShipment(waybill);
    }
}
