package com.ecommerce.app.module.delivery;

/**
 * Provider-agnostic delivery contract. Rapido is the first implementation;
 * Porter / Dunzo / in-house fleet can be added later without touching the
 * order or hyperlocal services.
 *
 * All methods are synchronous HTTP calls. Implementations must never throw
 * for "expected" negative answers (not serviceable / no rider) — those are
 * modelled in the result records. They may throw for transport failures.
 */
public interface DeliveryProvider {

    /** Stable provider code stored on delivery_job.provider. */
    String code();

    /** True when credentials are present so the app can fall back cleanly. */
    boolean isConfigured();

    /**
     * Ask the provider for a price + ETA + rider-availability snapshot for a
     * pickup->drop leg. Does NOT book anything.
     */
    QuoteResult quote(QuoteInput input) throws Exception;

    /**
     * Confirm a rider is currently available for this leg. Per the business
     * rule we only confirm an order when a rider can be assigned.
     */
    boolean riderAvailable(QuoteInput input) throws Exception;

    /**
     * Book the delivery. Called only AFTER payment success and after rider
     * availability has been (re)confirmed.
     */
    BookingResult book(BookingInput input) throws Exception;

    /** Cancel a previously booked delivery. */
    boolean cancel(String providerOrderId) throws Exception;

    /** Fetch live status for a booked delivery. */
    StatusResult status(String providerOrderId) throws Exception;

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    record QuoteInput(
            double pickupLat, double pickupLng, String pickupPincode,
            double dropLat, double dropLng, String dropPincode,
            int weightGrams
    ) {}

    record QuoteResult(
            boolean serviceable,
            boolean riderAvailable,
            double fee,
            int etaMin,
            double distanceKm,
            String providerQuoteId,
            String message
    ) {}

    record BookingInput(
            String orderId,
            String providerQuoteId,
            double pickupLat, double pickupLng,
            String pickupAddress, String pickupContactName, String pickupContactPhone,
            double dropLat, double dropLng,
            String dropAddress, String dropContactName, String dropContactPhone,
            double orderValue, int weightGrams
    ) {}

    record BookingResult(
            boolean success,
            String providerOrderId,
            String status,
            double fee,
            int etaMin,
            String riderName,
            String riderPhone,
            String trackingUrl,
            String rawPayload,
            String message
    ) {}

    record StatusResult(
            String status,
            String riderName,
            String riderPhone,
            String trackingUrl,
            String rawPayload
    ) {}
}
