package com.ecommerce.app.module.payment;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authoritative server-side shipping cost calculator.
 *
 * <p>Origin: Tirunelveli Town (627006), Tamil Nadu.
 * All products are non-documents (food items).
 * Rates effective 01-Feb-2026.
 *
 * <p>Zone map:
 * <pre>
 *   LOCAL     – Tirunelveli district (627xxx)
 *   LOCAL_OUT – Thoothukudi (628xxx) / Kanyakumari (629xxx)
 *   TN        – Tamil Nadu rest (600–649)
 *   KERALA    – Kerala (670–699)
 *   BANGALORE – Bangalore city (560–562)
 *   KARNATAKA – Rest of Karnataka (563–599)
 *   METRO     – HYD/SEC (500–502), DEL (110), BOM (400), CCU (700)
 *   REST      – Rest of India
 *   ANDAMAN   – Andaman & Nicobar (744xxx)
 * </pre>
 */
public final class ShippingCalculator {

    /** Estimated grams per unit item (no product-level weight field yet). */
    static final int GRAMS_PER_ITEM = 300;

    /**
     * Fallback weight (₹ shipping is weight-tiered) for piece/count
     * variants like "5 pcs" / "10 nos" whose label carries no mass.
     * Kept deliberately low so piece-based items land in the cheapest
     * shipping tier.
     */
    static final int PCS_GRAMS = 165;

    /** Matches a weight token in a variant label, e.g. "250g", "1.5 kg". */
    private static final Pattern WEIGHT_RE =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams)\\b");

    /** Matches piece/count variant labels, e.g. "5 pcs", "10 nos". */
    private static final Pattern PIECE_RE =
            Pattern.compile("\\b(pc|pcs|piece|pieces|no|nos|count|pack|packs)\\b");

    /** Orders at or above this merchandise total (₹) get free shipping. */
    static final double FREE_ABOVE_INR = 499.0;

    /** Rate entry: upto250g, upto500g, perKgAbove500 – all in ₹, non-document. */
    private static final class Rate {
        final double upto250;
        final double upto500;
        final double perKg;

        Rate(double upto250, double upto500, double perKg) {
            this.upto250 = upto250;
            this.upto500 = upto500;
            this.perKg   = perKg;
        }
    }

    private static final Map<String, Rate> ZONE_RATES;

    static {
        Map<String, Rate> m = new HashMap<>();
        //                             upto250  upto500  perKg
        m.put("LOCAL",     new Rate(  25,      25,      30  ));
        m.put("LOCAL_OUT", new Rate(  35,      35,      40  ));
        m.put("TN",        new Rate(  70,      70,      80  ));
        m.put("KERALA",    new Rate(  85,      85,     100  ));
        m.put("BANGALORE", new Rate(  80,      80,     100  ));
        m.put("KARNATAKA", new Rate(  85,      85,     105  ));
        m.put("METRO",     new Rate( 100,     100,     145  ));
        m.put("REST",      new Rate( 105,     105,     150  ));
        m.put("ANDAMAN",   new Rate( 225,     250,     500  ));
        ZONE_RATES = Collections.unmodifiableMap(m);
    }

    private static final Pattern PINCODE_RE = Pattern.compile("\\b(\\d{6})\\b");

    private ShippingCalculator() { /* utility class */ }

    /**
     * Calculate the shipping fee (₹) for a given order.
     *
     * @param shippingAddress  full shipping address string; pincode is extracted via regex
     * @param totalItems       total quantity of items (units) being shipped
     * @param merchandiseTotal order value before shipping (₹); returns 0 when ≥ FREE_ABOVE_INR
     * @return shipping fee in ₹ (never negative)
     */
    public static double calculate(String shippingAddress, int totalItems, double merchandiseTotal) {
        return calculateByGrams(shippingAddress, Math.max(1, totalItems) * GRAMS_PER_ITEM, merchandiseTotal);
    }

    /**
     * Calculate the shipping fee (₹) from an explicit total weight.
     * Callers derive the weight from each line item's variant label
     * via {@link #variantGrams(String)}.
     *
     * @param shippingAddress  full shipping address string; pincode is extracted via regex
     * @param totalGrams       total shipment weight in grams
     * @param merchandiseTotal order value before shipping (₹); returns 0 when ≥ FREE_ABOVE_INR
     * @return shipping fee in ₹ (never negative)
     */
    public static double calculateByGrams(String shippingAddress, int totalGrams, double merchandiseTotal) {
        if (merchandiseTotal >= FREE_ABOVE_INR) return 0.0;

        String pincode = extractPincode(shippingAddress);
        String zone    = getZone(pincode);
        Rate   rate    = ZONE_RATES.get(zone);

        int grams = Math.max(1, totalGrams);

        if (grams <= 250) return rate.upto250;
        if (grams <= 500) return rate.upto500;

        // Above 500 g: upto500 base + per-KG for each additional KG (ceiling division)
        int extraKg = (int) Math.ceil((grams - 500.0) / 1000.0);
        return rate.upto500 + extraKg * rate.perKg;
    }

    /**
     * Derive the per-unit weight (grams) of a variant from its label.
     *
     * <p>Variant labels encode the pack size, e.g. "250g", "500 g",
     * "1kg", or piece counts like "5 pcs". Weight labels are parsed to
     * their gram value; piece/count labels fall back to {@link #PCS_GRAMS}
     * (kept low on purpose); anything unrecognized (or no variant) falls
     * back to {@link #GRAMS_PER_ITEM}.
     *
     * @param label variant label (may be null for single-size products)
     * @return estimated weight in grams for one unit of this variant
     */
    public static int variantGrams(String label) {
        if (label == null || label.isBlank()) return GRAMS_PER_ITEM;
        String s = label.toLowerCase().trim();

        Matcher m = WEIGHT_RE.matcher(s);
        if (m.find()) {
            double value = Double.parseDouble(m.group(1));
            String unit  = m.group(2);
            int grams = unit.startsWith("kg")
                    ? (int) Math.round(value * 1000)
                    : (int) Math.round(value);
            return Math.max(1, grams);
        }

        // No weight token — treat piece/count packs as the lightest tier.
        if (PIECE_RE.matcher(s).find()) return PCS_GRAMS;

        return GRAMS_PER_ITEM;
    }

    /** Extract the first 6-digit numeric token from an address string. */
    static String extractPincode(String address) {
        if (address == null) return null;
        Matcher m = PINCODE_RE.matcher(address);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Derive shipping zone from a 6-digit Indian pincode string.
     * Returns "REST" for unknown / non-Indian pincodes.
     */
    static String getZone(String pincode) {
        if (pincode == null || !pincode.matches("\\d{6}")) return "REST";
        int p  = Integer.parseInt(pincode);
        int p3 = p / 1000;   // first 3 digits
        int p2 = p / 10000;  // first 2 digits

        // Local – Tirunelveli district (627xxx)
        if (p3 == 627) return "LOCAL";

        // Local Outer – Thoothukudi (628xxx) and Kanyakumari (629xxx)
        if (p3 == 628 || p3 == 629) return "LOCAL_OUT";

        // Tamil Nadu rest (600–649, excluding local zones above)
        if (p2 >= 60 && p2 <= 64) return "TN";

        // Kerala (670–699)
        if (p2 >= 67 && p2 <= 69) return "KERALA";

        // Bangalore only (560–562)
        if (p3 >= 560 && p3 <= 562) return "BANGALORE";

        // Karnataka rest (563–599)
        if (p2 >= 56 && p2 <= 59) return "KARNATAKA";

        // Andaman & Nicobar (744xxx)
        if (p3 == 744) return "ANDAMAN";

        // Major metros: HYD/SEC (500–502), DEL (110), BOM (400), CCU (700)
        if ((p3 >= 500 && p3 <= 502) || p3 == 110 || p3 == 400 || p3 == 700) return "METRO";

        return "REST";
    }
}
