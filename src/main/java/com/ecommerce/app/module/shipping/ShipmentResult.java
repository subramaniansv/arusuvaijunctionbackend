package com.ecommerce.app.module.shipping;

/**
 * Result of creating a shipment in Delhivery.
 */
public record ShipmentResult(
        String waybill,
        String status,
        boolean success
) {}
