/**
 * Returns / refunds policy - short and clear. Replace text once
 * the operations team confirms the final wording.
 */
import { Link } from 'react-router-dom'
import Seo from '../components/Seo'
import { breadcrumbLd } from '../lib/seo'
import './StaticPage.css'

export default function ReturnPolicy() {
  return (
    <div className="staticpage">
      <Seo
        title="Return & refund policy"
        description="Returns, refunds and replacement policy for Arusuvai Junction orders."
        path="/policy/returns"
        jsonLd={breadcrumbLd([
          { name: 'Home', path: '/' },
          { name: 'Returns', path: '/policy/returns' },
        ])}
      />
      <header className="staticpage__hero">
        <span className="staticpage__eyebrow">Legal</span>
        <h1 className="staticpage__title">Return &amp; refund policy</h1>
        <p className="staticpage__lead">
          Because our snacks are perishable, we follow a slightly different return process than
          a typical online store. Here&apos;s how it works.
        </p>
      </header>

      <div className="staticpage__body">
        <section className="staticpage__section">
          <h2>If something is wrong with your order</h2>
          <p>
            If your order arrives damaged, spoilt, or the wrong items were shipped, please get
            in touch within <strong>48 hours of delivery</strong>. We&apos;ll either re-ship the
            affected items free of cost or refund you in full - your choice.
          </p>
          <p>
            A quick photo of the issue helps us figure out what went wrong on our end and stops
            it from happening again.
          </p>
        </section>

        <section className="staticpage__section">
          <h2>What we cannot accept back</h2>
          <ul>
            <li>Open packets (food safety).</li>
            <li>Orders flagged after the 48-hour window - by then the freshness is hard to verify.</li>
            <li>Requests where the address provided at checkout was incorrect (we can re-ship for the cost of delivery).</li>
          </ul>
        </section>

        <section className="staticpage__section">
          <h2>How refunds are processed</h2>
          <p>
            Refunds are issued to the original payment method within 5–7 working days of being
            approved. UPI refunds usually land within a day, card refunds take a little longer.
          </p>
        </section>

        <section className="staticpage__section">
          <h2>Need help?</h2>
          <p>
            Open the order from <Link to="/orders">My orders</Link>, or message us directly via the{' '}
            <Link to="/contact">contact page</Link>. We read every message.
          </p>
        </section>
      </div>
    </div>
  )
}
