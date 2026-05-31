package com.ecommerce.app.module.shipping;

import java.util.List;

/**
 * Result of tracking a shipment via Delhivery.
 */
public record TrackingResult(
        String waybill,
        String status,
        String statusDate,
        String expectedDeliveryDate,
        List<TrackingScan> scans
) {}
