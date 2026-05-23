/**
 * Home page.
 *
 * Replica of frontendDemo/HomePage.tsx with two changes:
 *   1. Hero right-side image is a Carousel of banner images.
 *   2. Customer reviews section is a Carousel (auto-playing).
 *
 * All visuals are composed from the reusable components in
 * /src/components. Dummy data lives inline; reviews come from
 * `useHomeReviews()` which currently returns dummy data via a
 * setTimeout - swap the fetcher in /src/lib/reviews.js when the
 * backend endpoint exists.
 */
import { Link } from 'react-router-dom'
import {
  Leaf,
  Clock,
  Heart,
  Shield,
  ChevronRight,
  Sparkles,
  Phone,
  Mail,
  MapPin,
  MessageCircle,
} from 'lucide-react'

import {
  Container,
  Section,
  Button,
  Badge,
  ProductCard,
  ReviewCard,
  Carousel,
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

/* ---------------- Dummy data (replace with API later) ----------- */
const HERO_SLIDES = [
  {
    src: 'https://images.unsplash.com/photo-1635952346904-95f2ccfcd029?w=1000',
    caption: 'Sweet Ladoo',
  },
  {
    src: 'https://images.unsplash.com/photo-1610508500445-a4592435e27e?w=1000',
    caption: 'Traditional Murukku',
  },
  {
    src: 'https://images.unsplash.com/photo-1765360024331-25b63e85272e?w=1000',
    caption: 'Spicy Mixture',
  },
  {
    src: 'https://images.unsplash.com/photo-1610550246952-0c906d3aca7a?w=1000',
    caption: 'Athirasam',
  },
]

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
  { icon: Phone,         label: 'Call us',     value: '+91 98765 43210' },
  { icon: MessageCircle, label: 'WhatsApp',    value: '+91 98765 43210' },
  { icon: Mail,          label: 'Email',       value: 'hello@arusuvai.in' },
  { icon: MapPin,        label: 'Visit us',    value: 'T. Nagar, Chennai 600017' },
]

/* ------------------------ Component ----------------------------- */
export default function Home() {
  return (
    <div className="home">
      <Seo
        title={null /* uses brand default */}
        description={`${BRAND.description} Shop sugar-free traditional Indian snacks online — murukku, laddoos, mixture, sweets — made with nuts, seeds and millets.`}
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
              a: 'We use generous amounts of nuts (almonds, cashews, peanuts), seeds (sesame, flax, sunflower) and millets — all naturally protein-rich.',
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
              a: 'Never. Our snacks contain zero artificial preservatives, colours or flavours — just traditional ingredients.',
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
    <section className="home-hero">
      <div className="home-hero__pattern" aria-hidden="true" />
      <Container size="xl" className="home-hero__inner">
        <div className="home-hero__copy">
          <Badge variant="warning" className="home-hero__eyebrow">
            <Sparkles size={14} /> Freshly Made Daily
          </Badge>
          <h1 className="home-hero__title">
            Homemade<br />
            <span className="home-hero__title--accent">Traditional Snacks</span><br />
            &amp; Sweets
          </h1>
          <p className="home-hero__tamil">
            வீட்டில் தயாரிக்கப்பட்ட சுவையான உணவுகள்
          </p>
          <p className="home-hero__lead">
            Authentic Tamil snacks crafted with traditional recipes, no preservatives,
            and lots of love.
          </p>
          <div className="home-hero__ctas">
            <Button as={Link} to="/products" size="lg" rightIcon={<ChevronRight />}>
              Shop Now
            </Button>
            <Button as={Link} to="/products" size="lg" variant="secondary">
              View Menu
            </Button>
          </div>
        </div>

        <div className="home-hero__media">
          <div className="home-hero__blob home-hero__blob--yellow" aria-hidden="true" />
          <div className="home-hero__blob home-hero__blob--green" aria-hidden="true" />
          <Carousel
            autoPlay
            interval={4500}
            ariaLabel="Featured snacks"
            className="home-hero__carousel"
          >
            {HERO_SLIDES.map((s) => (
              <div className="home-hero__slide" key={s.src}>
                <img src={s.src} alt={s.caption} loading="eager" />
              </div>
            ))}
          </Carousel>
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
                  <img src={c.image} alt={c.name} loading="lazy" />
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

/* ----- Reviews (carousel, backed by useHomeReviews) ----------- */
function Reviews() {
  const { data, isLoading, isError } = useHomeReviews()

  return (
    <section className="home-section">
      <Container size="xl">
        <Section
          title="What Our Customers Say"
          subtitle="Trusted by thousands of happy customers"
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

        {!isLoading && !isError && data && (
          <Carousel
            autoPlay
            interval={6000}
            ariaLabel="Customer reviews"
            className="home-reviews ui-carousel--light-dots"
          >
            {data.map((r) => (
              <div className="home-reviews__slide" key={r.reviewId}>
                <ReviewCard review={r} />
              </div>
            ))}
          </Carousel>
        )}
        </Section>
      </Container>
    </section>
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
              Questions about an order or a bulk request? We're a message away.
            </p>
            <ul className="home-contact__list">
              {CONTACTS.map((c) => {
                const Icon = c.icon
                return (
                  <li key={c.label} className="home-contact__item">
                    <span className="home-contact__icon">
                      <Icon size={18} />
                    </span>
                    <div>
                      <div className="home-contact__label">{c.label}</div>
                      <div className="home-contact__value">{c.value}</div>
                    </div>
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
