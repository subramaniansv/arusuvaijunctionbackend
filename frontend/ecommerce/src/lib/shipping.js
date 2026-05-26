/**
 * India shipping cost calculator.
 *
 * Origin: Tirunelveli Town (627006), Tamil Nadu.
 * All products are non-documents (food items).
 *
 * Rates effective 01-Feb-2026 (NEW BOOKING CHARGES).
 *
 * Weight estimation: GRAMS_PER_ITEM grams per quantity unit.
 *
 * Zone map (from Tirunelveli origin):
 *   LOCAL     – Tirunelveli district (627xxx)
 *   LOCAL_OUT – Thoothukudi (628xxx) / Kanyakumari (629xxx)
 *   TN        – Tamil Nadu rest (600–649)
 *   KERALA    – Kerala (670–699)
 *   BANGALORE – Bangalore city (560–562)
 *   KARNATAKA – Rest of Karnataka (563–599)
 *   METRO     – HYD/SEC (500–502), DEL (110), BOM (400), CCU (700)
 *   REST      – Rest of India
 *   ANDAMAN   – Andaman & Nicobar (744xxx)
 */

/** Cart subtotal at or above this value → free shipping. */
export const FREE_ABOVE_INR = 999

/** Assumed weight per unit item in grams. */
export const GRAMS_PER_ITEM = 300

/**
 * Non-document rate table per zone (food products only).
 * All amounts in ₹.
 *   upto250 – flat charge for shipments ≤ 250 g
 *   upto500 – flat charge for 250 g < weight ≤ 500 g
 *   perKg   – additional charge per KG (ceiling) above 500 g
 */
const ZONE_RATES = {
  LOCAL:     { upto250:  25, upto500:  25, perKg:  30 },
  LOCAL_OUT: { upto250:  35, upto500:  35, perKg:  40 },
  TN:        { upto250:  70, upto500:  70, perKg:  80 },
  KERALA:    { upto250:  85, upto500:  85, perKg: 100 },
  BANGALORE: { upto250:  80, upto500:  80, perKg: 100 },
  KARNATAKA: { upto250:  85, upto500:  85, perKg: 105 },
  METRO:     { upto250: 100, upto500: 100, perKg: 145 },
  REST:      { upto250: 105, upto500: 105, perKg: 150 },
  ANDAMAN:   { upto250: 225, upto500: 250, perKg: 500 },
}

const ZONE_LABELS = {
  LOCAL:     'Local (Tirunelveli)',
  LOCAL_OUT: 'Local Outer (Thoothukudi / Kanyakumari)',
  TN:        'Tamil Nadu',
  KERALA:    'Kerala',
  BANGALORE: 'Bangalore',
  KARNATAKA: 'Karnataka',
  METRO:     'Metro (Hyderabad / Delhi / Mumbai / Kolkata)',
  REST:      'Rest of India',
  ANDAMAN:   'Andaman & Nicobar',
}

/**
 * Derive shipping zone from a 6-digit Indian pincode.
 * Falls back to REST for unknown / non-Indian pincodes.
 */
function getZone(pincode) {
  if (!pincode || !/^\d{6}$/.test(String(pincode).trim())) return 'REST'
  const p  = parseInt(pincode, 10)
  const p3 = Math.floor(p / 1000)   // first 3 digits
  const p2 = Math.floor(p / 10000)  // first 2 digits

  // Local – Tirunelveli district (627xxx)
  if (p3 === 627) return 'LOCAL'

  // Local Outer – Thoothukudi (628xxx) and Kanyakumari (629xxx)
  if (p3 === 628 || p3 === 629) return 'LOCAL_OUT'

  // Tamil Nadu rest (600–649, excluding local zones above)
  if (p2 >= 60 && p2 <= 64) return 'TN'

  // Kerala (670–699)
  if (p2 >= 67 && p2 <= 69) return 'KERALA'

  // Bangalore only (560–562)
  if (p3 >= 560 && p3 <= 562) return 'BANGALORE'

  // Karnataka rest (563–599)
  if (p2 >= 56 && p2 <= 59) return 'KARNATAKA'

  // Andaman & Nicobar (744xxx)
  if (p3 === 744) return 'ANDAMAN'

  // Major metros: HYD/SEC (500–502), DEL (110), BOM (400), CCU (700)
  if ((p3 >= 500 && p3 <= 502) || p3 === 110 || p3 === 400 || p3 === 700) return 'METRO'

  return 'REST'
}

/**
 * Calculate shipping fee in ₹ (whole rupees, never negative).
 *
 * @param {string} pincode            6-digit Indian pincode (or empty)
 * @param {number} totalItems         total quantity of all items in cart
 * @param {number} merchandiseTotal   cart value in ₹ (for free-shipping check)
 * @returns {number} shipping fee in ₹
 */
export function calcShipping(pincode, totalItems, merchandiseTotal) {
  if (merchandiseTotal >= FREE_ABOVE_INR) return 0

  const zone  = getZone(pincode)
  const rate  = ZONE_RATES[zone]
  const grams = Math.max(1, totalItems) * GRAMS_PER_ITEM

  if (grams <= 250) return rate.upto250
  if (grams <= 500) return rate.upto500

  // Above 500 g: base (upto500) + per-KG for each additional KG (ceiling)
  const extraKg = Math.ceil((grams - 500) / 1000)
  return rate.upto500 + extraKg * rate.perKg
}

/**
 * Human-readable zone description for a pincode.
 * Returns null for unknown/non-Indian pincodes.
 */
export function getZoneLabel(pincode) {
  if (!pincode || !/^\d{6}$/.test(String(pincode).trim())) return null
  return ZONE_LABELS[getZone(pincode)] ?? null
}

/**
 * Minimum possible shipping fee (used in Cart page before pincode is known).
 */
export const MIN_SHIPPING_INR = ZONE_RATES.LOCAL.upto250  // ₹25

