package com.ecommerce.app.module.delivery;

import com.ecommerce.app.module.quick.QuickStore;
import com.ecommerce.app.module.quick.QuickStoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Business-logic layer that sits between the order/payment flow and the raw
 * {@link DeliveryProvider} HTTP client. Responsible for:
 *   - confirming a rider is available before an order is confirmed
 *   - booking the delivery after payment success
 *   - persisting the resulting delivery_job
 *
 * Keeps provider plumbing (RapidoClient) separate from orchestration so a new
 * provider only needs a new DeliveryProvider impl.
 */
public class DeliveryOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(DeliveryOrchestrator.class);

    private final DeliveryProvider provider = RapidoClient.get();
    private final DeliveryJobRepository jobRepo = new DeliveryJobRepository();
    private final QuickStoreRepository storeRepo = new QuickStoreRepository();

    /**
     * Book a Rapido delivery for an order whose payment has already succeeded.
     * Re-confirms rider availability first; if none available, returns a
     * failed result WITHOUT booking so the caller can extend the ETA / ask
     * the user before committing. Persists the delivery_job on success.
     */
    public DeliveryProvider.BookingResult bookForOrder(
            UUID orderId, UUID storeId, String providerQuoteId,
            double dropLat, double dropLng, String dropAddress,
            String dropContactName, String dropContactPhone,
            double orderValue, int weightGrams) {

        QuickStore store = storeRepo.findById(storeId);
        if (store == null) {
            return failed("serving store not found");
        }
        if (!provider.isConfigured()) {
            return failed("delivery partner not configured");
        }

        jobRepo.create(orderId, provider.code(), providerQuoteId);

        // Re-confirm a rider is available before booking (business rule).
        try {
            DeliveryProvider.QuoteInput probe = new DeliveryProvider.QuoteInput(
                    store.getLat(), store.getLng(), store.getPincode(),
                    dropLat, dropLng, null, weightGrams);
            if (!provider.riderAvailable(probe)) {
                return failed("no rider available");
            }
        } catch (Exception e) {
            LOG.error("rider availability check failed order={}", orderId, e);
            return failed("could not confirm rider availability");
        }

        try {
            DeliveryProvider.BookingInput in = new DeliveryProvider.BookingInput(
                    orderId.toString(), providerQuoteId,
                    store.getLat(), store.getLng(), store.getAddress(),
                    store.getContactName(), store.getContactPhone(),
                    dropLat, dropLng, dropAddress, dropContactName, dropContactPhone,
                    orderValue, weightGrams);
            DeliveryProvider.BookingResult result = provider.book(in);
            if (result.success()) {
                jobRepo.saveBooking(orderId, result);
                LOG.info("delivery booked order={} providerOrderId={} eta={}min",
                        orderId, result.providerOrderId(), result.etaMin());
            } else {
                LOG.warn("delivery booking rejected order={} reason={}", orderId, result.message());
            }
            return result;
        } catch (Exception e) {
            LOG.error("delivery booking failed order={}", orderId, e);
            return failed("delivery booking failed: " + e.getMessage());
        }
    }

    private DeliveryProvider.BookingResult failed(String msg) {
        return new DeliveryProvider.BookingResult(
                false, null, "FAILED", 0, 0, null, null, null, null, msg);
    }
}
