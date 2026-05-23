/**
 * Privacy policy — plain, plain-English version. Not legal advice;
 * replace the body before going live in production.
 */
import Seo from '../components/Seo'
import { breadcrumbLd } from '../lib/seo'
import './StaticPage.css'

const LAST_UPDATED = 'May 2026'

export default function PrivacyPolicy() {
  return (
    <div className="staticpage">
      <Seo
        title="Privacy policy"
        description="How Arusuvai Junction collects, uses and protects your personal information."
        path="/policy/privacy"
        jsonLd={breadcrumbLd([
          { name: 'Home', path: '/' },
          { name: 'Privacy policy', path: '/policy/privacy' },
        ])}
      />
      <header className="staticpage__hero">
        <span className="staticpage__eyebrow">Legal</span>
        <h1 className="staticpage__title">Privacy policy</h1>
        <p className="staticpage__lead">
          Last updated: {LAST_UPDATED}. This page explains what we collect, why we collect
          it, and what we do (and don&apos;t do) with it.
        </p>
      </header>

      <div className="staticpage__body">
        <section className="staticpage__section">
          <h2>What we collect</h2>
          <ul>
            <li><strong>Account details</strong> — your name, email and (optionally) phone number when you create an account.</li>
            <li><strong>Order details</strong> — items ordered, shipping address and payment status. We never store full card numbers.</li>
            <li><strong>Contact form messages</strong> — anything you send us via the contact form, plus your email so we can reply.</li>
            <li><strong>Basic technical data</strong> — IP address and browser type, used only to detect abuse and keep the site stable.</li>
          </ul>
        </section>

        <section className="staticpage__section">
          <h2>What we don&apos;t do</h2>
          <ul>
            <li>We do not sell your data. Ever.</li>
            <li>We do not run third-party advertising trackers on the site.</li>
            <li>We do not share your details with any partner that isn&apos;t directly involved in fulfilling your order (payments, delivery).</li>
          </ul>
        </section>

        <section className="staticpage__section">
          <h2>Cookies</h2>
          <p>
            We use a small number of essential cookies to keep you signed in and to remember
            your cart between visits. We do not use third-party tracking cookies.
          </p>
        </section>

        <section className="staticpage__section">
          <h2>Your rights</h2>
          <p>
            You can request a copy of the data we hold on you, ask us to correct it, or ask
            us to delete your account entirely. Just email us at{' '}
            <a href="mailto:hello@arusuvai.in">hello@arusuvai.in</a> and we will get back
            to you within a few working days.
          </p>
        </section>

        <section className="staticpage__section">
          <h2>Updates to this policy</h2>
          <p>
            We may update this page from time to time. If we make a material change, we will
            note it at the top of the page with a fresh &ldquo;last updated&rdquo; date.
          </p>
        </section>
      </div>
    </div>
  )
}
