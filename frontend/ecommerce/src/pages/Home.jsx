/**
 * Home page.
 *
 * Hero is a static watercolor scene (src/assets/image.png) with brand
 * tagline + two CTAs. Customer reviews section is a Carousel (auto-playing).
 *
 * All visuals are composed from the reusable components in
 * /src/components. Dummy data lives inline; reviews come from
 * `useHomeReviews()` which currently returns dummy data via a
 * setTimeout - swap the fetcher in /src/lib/reviews.js when the
 * backend endpoint exists.
 */
import { Link } from 'react-router-dom'
import { useCallback, useEffect, useState } from 'react'
import {
  Leaf,
  Clock,
  Heart,
  Shield,
  ChevronLeft,
  ChevronRight,
  Phone,
  Mail,
  MapPin,
  MessageCircle,
} from 'lucide-react'

import {
  Container,
  Section,
  Button,
  ProductCard,
  RatingStars,
  Skeleton,
  Alert,
} from '../components'
import { useHomeReviews } from '../lib/reviews'
import Seo from '../components/Seo'
import {
  organizationLd,
  websiteLd,
  localBusinessLd,
  faqLd,
  BRAND,
} from '../lib/seo'
import './Home.css'

// Hero background image is now declared in Home.css via image-set()
// so the browser can pick AVIF, then WebP, then JPEG. The matching
// <link rel="preload" href="/hero.webp"> in index.html primes the LCP
// image to download in parallel with the JS bundle.

/* ---------------- Dummy data (replace with API later) ----------- */

const CATEGORIES = [
  { name: 'Murukku',   tamil: 'முறுக்கு',  image: 'https://images.unsplash.com/photo-1610508500445-a4592435e27e?w=400', color: 'var(--brand-green-light)' },
  { name: 'Seedai',    tamil: 'சீடை',      image: 'https://images.unsplash.com/photo-1605276277265-84f97980a425?w=400', color: 'var(--brand-yellow-light)' },
  { name: 'Sweets',    tamil: 'இனிப்புகள்', image: 'https://images.unsplash.com/photo-1635952346904-95f2ccfcd029?w=400', color: 'var(--brand-green-light)' },
  { name: 'Mixture',   tamil: 'மிக்சர்',    image: 'https://images.unsplash.com/photo-1765360024331-25b63e85272e?w=400', color: 'var(--brand-yellow-light)' },
  { name: 'Chips',     tamil: 'சிப்ஸ்',     image: 'https://images.unsplash.com/photo-1762884601729-0eeeafbdfb8a?w=400', color: 'var(--brand-green-light)' },
  { name: 'Athirasam', tamil: 'அதிரசம்',   image: 'https://images.unsplash.com/photo-1610550246952-0c906d3aca7a?w=400', color: 'var(--brand-yellow-light)' },
]

const FEATURED_PRODUCTS = [
  {
    productId: 'p1',
    name: 'Traditional Murukku',
    price: 180,
    mrp: 220,
    primaryImageUrl: 'https://images.unsplash.com/photo-1610508500445-a4592435e27e?w=600',
    stockQuantity: 25,
    averageRating: 4.8,
    reviewCount: 142,
    isOrganic: true,
  },
  {
    productId: 'p2',
    name: 'Sweet Ladoo',
    price: 220,
    primaryImageUrl: 'https://images.unsplash.com/photo-1635952346904-95f2ccfcd029?w=600',
    stockQuantity: 18,
    averageRating: 4.9,
    reviewCount: 96,
  },
  {
    productId: 'p3',
    name: 'Spicy Mixture',
    price: 160,
    primaryImageUrl: 'https://images.unsplash.com/photo-1765360024331-25b63e85272e?w=600',
    stockQuantity: 40,
    averageRating: 4.6,
    reviewCount: 58,
    isVeg: true,
  },
  {
    productId: 'p4',
    name: 'Banana Chips',
    price: 140,
    mrp: 160,
    primaryImageUrl: 'https://images.unsplash.com/photo-1762884601729-0eeeafbdfb8a?w=600',
    stockQuantity: 30,
    averageRating: 4.7,
    reviewCount: 81,
  },
]

const FEATURES = [
  { icon: Leaf,   title: 'Homemade',         description: 'Traditional recipes prepared fresh in our kitchen.' },
  { icon: Shield, title: 'No Preservatives', description: '100% natural ingredients, no artificial additives.' },
  { icon: Clock,  title: 'Freshly Prepared', description: 'Made fresh daily to ensure the best taste.' },
  { icon: Heart,  title: 'Made with Love',   description: 'Every batch is crafted with care and tradition.' },
]

/* Placeholder contact info / quick links - replace with real data later */
const QUICK_LINKS = [
  { label: 'About us',                  to: '/about' },
  { label: 'All products',              to: '/products' },
  { label: 'Track an order',            to: '/orders' },
  { label: 'Bulk / corporate orders',   to: '/contact' },
  { label: 'Return policy',             to: '/policy/returns' },
  { label: 'Privacy policy',            to: '/policy/privacy' },
]

const CONTACTS = [
  {
    icon: Phone,
    label: 'Call us',
    value: '+91 95974 51463',
    href: 'tel:+919597451463',
  },
  {
    icon: MessageCircle,
    label: 'WhatsApp',
    value: '+91 95974 51463',
    href: 'https://wa.me/919597451463',
  },
  {
    icon: Mail,
    label: 'Email support',
    value: 'support@arusuvaijunction.com',
    href: 'mailto:support@arusuvaijunction.com',
  },
  {
    icon: MapPin,
    label: 'Visit us',
    value: (
      <>
        6/A, Matha Middle Street,<br />
        Tirunelveli Town,<br />
        Tamil Nadu 627006
      </>
    ),
    href: 'https://www.google.com/maps/search/?api=1&query=Arusuvai+Junction+Tirunelveli',
  },
]

/* ------------------------ Component ----------------------------- */
export default function Home() {
  return (
    <div className="home">
      <Seo
        title={null /* uses brand default */}
        description={`${BRAND.description} Shop sugar-free traditional Indian snacks online - murukku, laddoos, mixture, sweets - made with nuts, seeds and millets.`}
        path="/"
        keywords={BRAND.defaultKeywords}
        jsonLd={[
          organizationLd(),
          websiteLd(),
          localBusinessLd(),
          faqLd([
            {
              q: 'Are Arusuvai Junction snacks really sugar-free?',
              a: 'Yes. We sweeten our snacks with palm jaggery, dates, or country sugar instead of refined white sugar. Each product page lists the exact ingredients.',
            },
            {
              q: 'What makes your snacks high in protein?',
              a: 'We use generous amounts of nuts (almonds, cashews, peanuts), seeds (sesame, flax, sunflower) and millets - all naturally protein-rich.',
            },
            {
              q: 'Do you ship pan-India?',
              a: 'Yes, we ship across India. Orders are dispatched within 1–2 business days from Tirunelveli, Tamil Nadu.',
            },
            {
              q: 'How long do the snacks stay fresh?',
              a: 'Most products stay fresh for 30–45 days at room temperature in an air-tight container. Specific shelf life is shown on each product page.',
            },
            {
              q: 'Are the snacks made with preservatives?',
              a: 'Never. Our snacks contain zero artificial preservatives, colours or flavours - just traditional ingredients.',
            },
          ]),
        ]}
      />
      <Hero />
      <Categories />
      <Featured />
      <WhyChooseUs />
      <Reviews />
      <ContactStrip />
    </div>
  )
}

/* ----- Hero ---------------------------------------------------- */
function Hero() {
  return (
    <section
      className="home-hero"
      aria-label="Arusuvai Junction - fresh, homemade traditional South Indian foods"
    >
      <div className="home-hero__veil" aria-hidden="true" />
      <Container size="xl" className="home-hero__inner">
        <div className="home-hero__copy">
          <h1 className="home-hero__title">
            <span className="home-hero__title--line-1">Fresh. Homemade.</span>
            <span className="home-hero__title--line-2">Arusuvai Junction.</span>
          </h1>

          <div className="home-hero__divider" aria-hidden="true">
            <span />
            <Leaf size={14} />
            <span />
          </div>

          <p className="home-hero__lead">
            Traditional South Indian goodness crafted with authentic ingredients,
            homemade care, and timeless recipes.
          </p>

          <div className="home-hero__ctas">
            <Link to="/products" className="home-hero__btn home-hero__btn--primary">
              Explore Products
            </Link>
            <Link to="/contact" className="home-hero__btn home-hero__btn--secondary">
              Contact Us
            </Link>
          </div>
        </div>
      </Container>
    </section>
  )
}

/* ----- Categories --------------------------------------------- */
function Categories() {
  return (
    <section className="home-section">
      <Container size="xl">
        <Section
          title="Shop by Category"
          subtitle="Explore our authentic Tamil delicacies"
          spacing="md"
        >
          <div className="home-cats">
            {CATEGORIES.map((c) => (
              <Link
                key={c.name}
                to={`/products?category=${encodeURIComponent(c.name.toLowerCase())}`}
                className="home-cat"
              >
                <div className="home-cat__media" style={{ background: c.color }}>
                  <img src={c.image} alt={c.name} loading="lazy" decoding="async" />
                </div>
                <h3 className="home-cat__name">{c.name}</h3>
                <p className="home-cat__tamil">{c.tamil}</p>
              </Link>
            ))}
          </div>
        </Section>
      </Container>
    </section>
  )
}

/* ----- Featured products -------------------------------------- */
function Featured() {
  return (
    <section className="home-section home-section--tint">
      <Container size="xl">
        <Section
          title="Featured Products"
          subtitle="Handpicked favorites from our kitchen"
          action={
            <Link to="/products" className="home-link">
              View All <ChevronRight size={16} />
            </Link>
          }
          spacing="md"
        >
          <div className="home-grid-4">
            {FEATURED_PRODUCTS.map((p) => (
              <ProductCard
                key={p.productId}
                product={p}
                onAddToCart={(id) => console.log('add', id)}
              />
            ))}
          </div>
        </Section>
      </Container>
    </section>
  )
}

/* ----- Why choose us ------------------------------------------ */
function WhyChooseUs() {
  return (
    <section className="home-why">
      <Container size="xl">
        <Section
          title="Why Choose Us"
          subtitle="Traditional taste with modern convenience"
          spacing="md"
        >
          <div className="home-features">
            {FEATURES.map((f) => {
              const Icon = f.icon
              return (
                <div key={f.title} className="home-feature">
                  <div className="home-feature__icon">
                    <Icon size={28} />
                  </div>
                  <h3 className="home-feature__title">{f.title}</h3>
                  <p className="home-feature__desc">{f.description}</p>
                </div>
              )
            })}
          </div>
        </Section>
      </Container>
    </section>
  )
}

/* ----- Reviews (peek carousel, backed by useHomeReviews) ----- */
function Reviews() {
  const { data, isLoading, isError } = useHomeReviews()

  return (
    <section className="home-section">
      <Container size="xl">
        <Section
          title="What our customers say"
          subtitle="Verified reviews from across India"
          spacing="md"
        >
        {isLoading && (
          <div className="home-reviews__skeletons">
            {[0, 1, 2].map((i) => (
              <div className="home-review-skeleton" key={i}>
                <Skeleton width="100%" height={20} />
                <Skeleton width="80%" height={14} />
                <Skeleton width="100%" height={60} />
                <div className="home-review-skeleton__foot">
                  <Skeleton width={36} height={36} radius="pill" />
                  <Skeleton width={120} height={14} />
                </div>
              </div>
            ))}
          </div>
        )}

        {isError && (
          <Alert variant="warning" title="Couldn't load reviews">
            Please try again in a moment.
          </Alert>
        )}

        {!isLoading && !isError && data && data.length > 0 && (
          <ReviewsPeek reviews={data} />
        )}

        {!isLoading && !isError && data && data.length === 0 && (
          <p className="home-reviews__empty">
            Be the first to leave a review - every purchased product has a review form.
          </p>
        )}
        </Section>
      </Container>
    </section>
  )
}

/**
 * ReviewsPeek - responsive multi-card carousel.
 *
 * Shows N reviews side by side, all at full opacity:
 *   >= 1024px → 3 cards
 *   >= 640px  → 2 cards
 *   < 640px   → 1 card
 *
 * Arrows + dot pagination sit BELOW the row. Auto-advances every 6s;
 * pauses on hover/focus. The dots count corresponds to how many
 * "pages" of `visible` cards the reviews divide into.
 */
function useVisibleCount() {
  const get = () => {
    if (typeof window === 'undefined') return 3
    if (window.matchMedia('(min-width: 1024px)').matches) return 3
    if (window.matchMedia('(min-width: 640px)').matches) return 2
    return 1
  }
  const [n, setN] = useState(get)
  useEffect(() => {
    const onResize = () => setN(get())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  return n
}

function ReviewsPeek({ reviews }) {
  const count = reviews.length
  const visible = Math.min(useVisibleCount(), count)
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)

  // Clamp index when visible count changes (e.g. resize).
  useEffect(() => { setIndex(0) }, [visible, count])

  const next = useCallback(
    () => setIndex((i) => (i + 1) % count),
    [count],
  )
  const prev = useCallback(
    () => setIndex((i) => (i - 1 + count) % count),
    [count],
  )

  useEffect(() => {
    if (paused || count <= visible) return undefined
    const t = setInterval(next, 6000)
    return () => clearInterval(t)
  }, [paused, next, count, visible])

  // Build the visible slice with wrap-around so the carousel can loop.
  const slots = Array.from({ length: visible }, (_, k) => {
    const r = reviews[(index + k) % count]
    return { key: `${index}-${k}-${r.reviewId}`, review: r }
  })

  return (
    <div
      className="home-reviews-peek"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      role="region"
      aria-roledescription="carousel"
      aria-label="Customer reviews"
    >
      <div
        className="home-reviews-peek__row"
        style={{ '--peek-visible': visible }}
      >
        {slots.map(({ key, review }) => (
          <article key={key} className="home-review-simple">
            <RatingStars value={review.rating} size="md" />
            <p className="home-review-simple__body">
              {review.body ? `“${review.body}”` : ''}
            </p>
            <div className="home-review-simple__name">
              - {review.user?.name || 'Customer'}
            </div>
          </article>
        ))}
      </div>

      <div className="home-reviews-peek__controls" aria-label="Carousel navigation">
        <button
          type="button"
          className="home-reviews-peek__arrow"
          onClick={prev}
          aria-label="Previous review"
        >
          <ChevronLeft size={20} />
        </button>

        <div className="home-reviews-peek__dots" role="tablist">
          {reviews.map((r, i) => (
            <button
              key={r.reviewId}
              type="button"
              role="tab"
              aria-selected={i === index}
              aria-label={`Go to review ${i + 1}`}
              className={`home-reviews-peek__dot no-touch-min ${i === index ? 'is-active' : ''}`}
              onClick={() => setIndex(i)}
            />
          ))}
        </div>

        <button
          type="button"
          className="home-reviews-peek__arrow"
          onClick={next}
          aria-label="Next review"
        >
          <ChevronRight size={20} />
        </button>
      </div>
    </div>
  )
}

/* ----- Contact + Quick links strip ---------------------------- */
function ContactStrip() {
  return (
    <section className="home-contact">
      <Container size="xl">
        <div className="home-contact__grid">
          <div>
            <h3 className="home-contact__heading">Get in touch</h3>
            <p className="home-contact__sub">
              Have a question about an order, ingredients, or a bulk gift
              request? Reach us on WhatsApp or email - we usually reply
              within a few hours, Mon–Sat.
            </p>
            <ul className="home-contact__list">
              {CONTACTS.map((c) => {
                const Icon = c.icon
                const isExternal = c.href && /^(https?:|mailto:|tel:)/.test(c.href)
                const inner = (
                  <>
                    <span className="home-contact__icon">
                      <Icon size={18} />
                    </span>
                    <div>
                      <div className="home-contact__label">{c.label}</div>
                      <div className="home-contact__value">{c.value}</div>
                    </div>
                  </>
                )
                return (
                  <li key={c.label} className="home-contact__item">
                    {c.href ? (
                      <a
                        href={c.href}
                        className="home-contact__link"
                        {...(isExternal && c.href.startsWith('http')
                          ? { target: '_blank', rel: 'noopener noreferrer' }
                          : {})}
                      >
                        {inner}
                      </a>
                    ) : (
                      inner
                    )}
                  </li>
                )
              })}
            </ul>
          </div>

          <div>
            <h3 className="home-contact__heading">Quick links</h3>
            <p className="home-contact__sub">Jump straight to where you need.</p>
            <ul className="home-quick-links">
              {QUICK_LINKS.map((q) => (
                <li key={q.to}>
                  <Link to={q.to} className="home-quick-links__a">
                    <ChevronRight size={14} />
                    <span>{q.label}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Container>
    </section>
  )
}
