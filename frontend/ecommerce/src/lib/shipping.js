/**
 * India shipping cost calculator.
 *
 * Origin: Tirunelveli, Tamil Nadu (627xxx).
 * Destination zone is derived from the 6-digit Indian pincode.
 * Rates are typical Delhivery / Shiprocket small-parcel rates (2025).
 *
 * Weight estimation: GRAMS_PER_ITEM grams per cart item line
 * (no product-level weight field yet; refine once weights are added).
 *
 * Zone map (from Tirunelveli origin):
 *   A – Local  (Tirunelveli / Thoothukudi / Nagercoil)
 *   B – Tamil Nadu (rest of state)
 *   C – South India (Kerala, Karnataka, Andhra Pradesh, Telangana)
 *   D – Rest of India (mainland)
 *   E – Special (North East, J&K, Andaman & Nicobar, Lakshadweep)
 */

/** Cart subtotal at or above this value → free shipping. */
export const FREE_ABOVE_INR = 999

/** Assumed weight per distinct cart item line in grams. */
export const GRAMS_PER_ITEM = 300

/**
 * Rate table per zone.
 *   base      – ₹ for the first 500 g (minimum charge)
 *   perSlab   – ₹ per additional 500 g slab (or fraction thereof)
 *   slabGrams – slab size in grams
 */
const ZONE_RATES = {
  A: { base: 65,  perSlab: 25, slabGrams: 500 },
  B: { base: 75,  perSlab: 30, slabGrams: 500 },
  C: { base: 90,  perSlab: 40, slabGrams: 500 },
  D: { base: 120, perSlab: 50, slabGrams: 500 },
  E: { base: 160, perSlab: 70, slabGrams: 500 },
}

const ZONE_LABELS = {
  A: 'Local (Tirunelveli / Thoothukudi)',
  B: 'Tamil Nadu',
  C: 'South India',
  D: 'Rest of India',
  E: 'North East / J&K / Andaman',
}

/**
 * Derive shipping zone from a 6-digit Indian pincode.
 * Falls back to zone D (mainland) for unknown / non-Indian pincodes.
 */
function getZone(pincode) {
  if (!pincode || !/^\d{6}$/.test(String(pincode).trim())) return 'D'
  const p  = parseInt(pincode, 10)
  const p3 = Math.floor(p / 1000)   // first 3 digits
  const p2 = Math.floor(p / 10000)  // first 2 digits

  // Zone A – Local (Tirunelveli 627, Thoothukudi 628, Nagercoil 629)
  if (p3 === 627 || p3 === 628 || p3 === 629) return 'A'

  // Zone B – Tamil Nadu (600–649)
  if (p2 >= 60 && p2 <= 64) return 'B'

  // Zone C – South India
  // Kerala (670–695), Karnataka (560–595), AP / Telangana (500–535)
  if ((p2 >= 67 && p2 <= 69) ||
      (p2 >= 56 && p2 <= 59) ||
      (p2 >= 50 && p2 <= 53)) return 'C'

  // Zone E – North East (780–799), J&K/HP (180–194), Andaman (744)
  if ((p2 >= 78 && p2 <= 79) ||
      (p2 >= 18 && p2 <= 19) ||
      p3 === 744) return 'E'

  // Zone D – everything else (mainland India)
  return 'D'
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

  // First 500 g → base rate; each additional 500 g slab (ceiling) → perSlab
  const extraSlabs = Math.max(0, Math.ceil((grams - 500) / rate.slabGrams))
  return rate.base + extraSlabs * rate.perSlab
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
export const MIN_SHIPPING_INR = ZONE_RATES.A.base  // ₹65
