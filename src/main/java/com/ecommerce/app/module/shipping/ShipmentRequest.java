package com.ecommerce.app.module.shipping;

/**
 * Input data for creating a Delhivery shipment.
 */
public record ShipmentRequest(
        String customerName,
        String address,
        String pincode,
        String city,
        String state,
        String phone,
        String orderId,
        double totalAmount,
        int weightGrams,
        int widthCm,
        int heightCm,
        int lengthCm
) {}
