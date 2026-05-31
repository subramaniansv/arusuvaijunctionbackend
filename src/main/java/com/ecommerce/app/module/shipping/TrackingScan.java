package com.ecommerce.app.module.shipping;

/**
 * A single tracking scan event from Delhivery.
 */
public record TrackingScan(
        String activity,
        String dateTime,
        String location,
        String instructions
) {}
