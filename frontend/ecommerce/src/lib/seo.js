/**
 * Centralised SEO constants and JSON-LD generators.
 *
 * Update SITE_URL, BRAND, BUSINESS, etc. in one place and every page
 * picks up the change.
 */

// Public origin used in canonical URLs, OG tags, sitemaps.
// Override with VITE_SITE_URL at build-time (e.g. on Render).
export const SITE_URL =
  import.meta.env.VITE_SITE_URL || 'https://www.arusuvaijunction.com'

export const BRAND = {
  name: 'Arusuvai Junction',
  legalName: 'Arusuvai Junction',
  shortName: 'Arusuvai Junction',
  tagline:
    'Traditional and modern Indian snacks made with nuts, seeds and millets — no white sugar, packed with natural protein.',
  description:
    'Arusuvai Junction makes traditional and modern Indian snacks crafted with nuts, seeds, millets and natural sweeteners. Sugar-free, protein-rich, preservative-free — flavours of home, made the healthy way.',
  defaultKeywords: [
    'traditional indian snacks',
    'healthy snacks online india',
    'sugar free snacks',
    'protein rich snacks',
    'nuts and seeds snacks',
    'millet snacks',
    'homemade indian sweets',
    'natural snacks',
    'arusuvai junction',
    'tamil nadu traditional foods',
  ],
  logo: `${SITE_URL}/favicon.svg`,
  // Replace with a 1200x630 JPG/PNG once you have one in /public.
  defaultOgImage: `${SITE_URL}/og-default.jpg`,
}

export const BUSINESS = {
  // Used in LocalBusiness JSON-LD + Contact page.
  streetAddress: '6/A, Matha Middle Street',
  addressLocality: 'Tirunelveli Town',
  addressRegion: 'Tamil Nadu',
  postalCode: '627006',
  addressCountry: 'IN',
  telephone: '+919597451463',
  email: 'support@arusuvaijunction.com',
  // Lat/Long are optional but boost local-pack ranking.
  // Drop pin in Google Maps for the actual address and paste here.
  geo: null, // e.g. { latitude: 8.7139, longitude: 77.7567 }
  openingHours: 'Mo-Sa 09:00-19:00',
  priceRange: '₹₹',
}

export const SOCIAL = {
  instagram: 'https://www.instagram.com/arusuvai_junction/',
  facebook: '',
  youtube: '',
  twitter: '',
}

const sameAs = Object.values(SOCIAL).filter(Boolean)

// ────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────

/** Build an absolute canonical URL from a path. */
export function absoluteUrl(path = '/') {
  if (/^https?:\/\//i.test(path)) return path
  const base = SITE_URL.replace(/\/+$/, '')
  const p = path.startsWith('/') ? path : `/${path}`
  return `${base}${p}`
}

// ────────────────────────────────────────────────────────────────────
// JSON-LD generators
// Each returns a plain JS object - JSON.stringify it inside <script>.
// ────────────────────────────────────────────────────────────────────

export function organizationLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: BRAND.name,
    legalName: BRAND.legalName,
    url: SITE_URL,
    logo: BRAND.logo,
    description: BRAND.description,
    sameAs,
    contactPoint: [
      {
        '@type': 'ContactPoint',
        contactType: 'customer support',
        telephone: BUSINESS.telephone,
        email: BUSINESS.email,
        areaServed: 'IN',
        availableLanguage: ['en', 'ta'],
      },
    ],
  }
}

export function websiteLd() {
  return {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: BRAND.name,
    url: SITE_URL,
    description: BRAND.tagline,
    inLanguage: 'en-IN',
    potentialAction: {
      '@type': 'SearchAction',
      target: `${SITE_URL}/products?search={search_term_string}`,
      'query-input': 'required name=search_term_string',
    },
  }
}

export function localBusinessLd() {
  const ld = {
    '@context': 'https://schema.org',
    '@type': 'FoodEstablishment',
    '@id': `${SITE_URL}/#localbusiness`,
    name: BRAND.name,
    image: BRAND.logo,
    url: SITE_URL,
    telephone: BUSINESS.telephone,
    email: BUSINESS.email,
    priceRange: BUSINESS.priceRange,
    servesCuisine: ['Indian', 'South Indian', 'Tamil', 'Healthy snacks'],
    address: {
      '@type': 'PostalAddress',
      streetAddress: BUSINESS.streetAddress,
      addressLocality: BUSINESS.addressLocality,
      addressRegion: BUSINESS.addressRegion,
      postalCode: BUSINESS.postalCode,
      addressCountry: BUSINESS.addressCountry,
    },
    openingHours: BUSINESS.openingHours,
    sameAs,
  }
  if (BUSINESS.geo) {
    ld.geo = {
      '@type': 'GeoCoordinates',
      latitude: BUSINESS.geo.latitude,
      longitude: BUSINESS.geo.longitude,
    }
  }
  return ld
}

export function breadcrumbLd(items = []) {
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map((it, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: it.name,
      item: absoluteUrl(it.path),
    })),
  }
}

/**
 * @param product Backend product shape:
 *   { id, productId, name, description, price, primaryImageUrl,
 *     images:[{imageUrl}], variants:[{sku,price,stock}], stock, sku, brand }
 */
export function productLd(product, pathname) {
  if (!product) return null
  const id = product.id ?? product.productId
  const url = absoluteUrl(pathname || `/products/${id}`)
  const images = Array.isArray(product.images)
    ? product.images.map((i) => i.imageUrl || i.url).filter(Boolean)
    : []
  const image = images.length
    ? images
    : product.primaryImageUrl
    ? [product.primaryImageUrl]
    : [BRAND.defaultOgImage]

  const variants = Array.isArray(product.variants) ? product.variants : []
  const inStock =
    (variants.length
      ? variants.some((v) => (v.stock ?? 0) > 0)
      : (product.stock ?? 0) > 0) || product.inStock === true

  const offers =
    variants.length > 1
      ? {
          '@type': 'AggregateOffer',
          priceCurrency: 'INR',
          lowPrice: Math.min(...variants.map((v) => Number(v.price) || 0)),
          highPrice: Math.max(...variants.map((v) => Number(v.price) || 0)),
          offerCount: variants.length,
          availability: inStock
            ? 'https://schema.org/InStock'
            : 'https://schema.org/OutOfStock',
          url,
        }
      : {
          '@type': 'Offer',
          priceCurrency: 'INR',
          price:
            (variants[0] && variants[0].price) ??
            product.price ??
            0,
          availability: inStock
            ? 'https://schema.org/InStock'
            : 'https://schema.org/OutOfStock',
          url,
          itemCondition: 'https://schema.org/NewCondition',
        }

  return {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description || BRAND.tagline,
    image,
    sku: product.sku || String(id),
    brand: { '@type': 'Brand', name: product.brand || BRAND.name },
    offers,
  }
}

export function faqLd(faqs = []) {
  return {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: faqs.map((q) => ({
      '@type': 'Question',
      name: q.q,
      acceptedAnswer: { '@type': 'Answer', text: q.a },
    })),
  }
}
