package com.ecommerce.app.module.shipping;

/**
 * Result of a Delhivery rate calculation.
 */
public record RateResult(
        double totalAmount,
        String zone,
        double chargedWeightGrams
) {}
