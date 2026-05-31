package com.ecommerce.app.module.shipping;

/**
 * Result of a pincode serviceability check.
 */
public record ServiceabilityResult(
        boolean serviceable,
        boolean codAvailable,
        int estimatedDays,
        String district
) {}
