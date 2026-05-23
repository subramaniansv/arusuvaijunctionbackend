/**
 * About us — static informational page.
 *
 * Shipped to fix the 404s referenced from the home page footer.
 * Content is intentionally plain text so it's easy to refresh
 * without code changes.
 */
import { Link } from 'react-router-dom'
import { Leaf, Heart, Clock, ShieldCheck } from 'lucide-react'
import { Button } from '../components'
import Seo from '../components/Seo'
import { organizationLd, breadcrumbLd } from '../lib/seo'
import './StaticPage.css'

const PILLARS = [
  { icon: Leaf,        title: 'Homemade goodness',  body: 'Every batch is cooked the same way it would be in a Tamil home — no shortcuts, no fillers.' },
  { icon: ShieldCheck, title: 'Zero preservatives', body: 'We use only the ingredients you would use in your own kitchen. Nothing artificial, ever.' },
  { icon: Clock,       title: 'Fresh, not stocked', body: 'Snacks are prepared in small batches and shipped as soon as they cool. No warehouse aging.' },
  { icon: Heart,       title: 'Cooked with love',   body: 'Recipes have been passed down for generations. We make what we would happily feed our own family.' },
]

export default function About() {
  return (
    <div className="staticpage">
      <Seo
        title="About Arusuvai Junction — traditional Tamil snacks, made the healthy way"
        description="The story behind Arusuvai Junction: family recipes, traditional methods, no white sugar, no preservatives. Healthy Indian snacks made with nuts, seeds and millets."
        path="/about"
        jsonLd={[
          organizationLd(),
          breadcrumbLd([
            { name: 'Home', path: '/' },
            { name: 'About', path: '/about' },
          ]),
        ]}
      />
      <header className="staticpage__hero">
        <span className="staticpage__eyebrow">About us</span>
        <h1 className="staticpage__title">Traditional Tamil snacks, made the way they should be</h1>
        <p className="staticpage__lead">
          Arusuvai started as a small family kitchen in Chennai with a single goal — to share
          homemade Tamil snacks with families who don&apos;t have the time (or the patient
          grandmother) to make them at home.
        </p>
      </header>

      <div className="staticpage__body staticpage__body--split">
        <div className="staticpage__section">
          <h2>Our story</h2>
          <p>
            What started as a Diwali order for a few neighbours grew into a tiny commercial
            kitchen in T. Nagar. Today we ship across India, but the recipes haven&apos;t changed:
            our murukku still gets its crunch from the same hand-press my grandmother used, and
            the laddoos still come out of the same brass pan.
          </p>
          <p>
            We&apos;re proudly small. Every order goes through one of three people on our team,
            which is exactly how we like it.
          </p>

          <blockquote className="staticpage__quote">
            &ldquo;Arusuvai&rdquo; means &ldquo;six tastes&rdquo; in Tamil — sweet, sour, salty,
            bitter, pungent, and astringent. A meal is incomplete without all of them.
          </blockquote>

          <h2>What we stand for</h2>
          <ul>
            {PILLARS.map(({ icon: Icon, title, body }) => (
              <li key={title}>
                <strong style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                  <Icon size={16} aria-hidden="true" /> {title} —
                </strong>{' '}
                {body}
              </li>
            ))}
          </ul>
        </div>

        <aside className="staticpage__aside">
          <div className="staticpage__card">
            <h3>By the numbers</h3>
            <p style={{ margin: 0 }}>
              <strong>12+</strong> family recipes &middot; <strong>5,000+</strong> happy
              households &middot; shipping across <strong>India</strong>.
            </p>
          </div>
          <div className="staticpage__card">
            <h3>Want a custom order?</h3>
            <p style={{ margin: 0 }}>
              We do bulk hampers for weddings, festivals and offices. Drop us a note and we&apos;ll
              put together a quote within a day.
            </p>
            <Button as={Link} to="/contact" size="md">Talk to us</Button>
          </div>
        </aside>
      </div>
    </div>
  )
}
