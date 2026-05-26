/**
 * Order detail page (protected).
 *
 * Reads :orderId from the route. If the user just placed this order
 * (router state.justPlaced from Checkout) we render a celebratory
 * banner + the WhatsApp CTA prominently. After refresh the order is
 * refetched from the server; the WhatsApp transient fields are only
 * present on the checkout response, so we keep them in cache and
 * also tolerate them being missing.
 */
import { useEffect } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  CheckCircle2, MapPin, Phone, Package, Truck, Home, XCircle, Clock,
  ArrowLeft, MessageCircle, ChevronRight,
} from 'lucide-react'

import {
  Container,
  Card,
  Badge,
  Button,
  Divider,
  PriceTag,
  Skeleton,
  Alert,
} from '../components'
import {
  useOrder,
  ORDER_STATUS_VARIANT,
  formatOrderDate,
  shortOrderId,
} from '../lib/orders'
import './OrderDetail.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

/* Status timeline definition. CANCELLED is rendered as a separate
 * side-track when applicable. */
const TIMELINE = [
  { key: 'PENDING',   label: 'Placed',    Icon: Clock },
  { key: 'CONFIRMED', label: 'Confirmed', Icon: CheckCircle2 },
  { key: 'SHIPPED',   label: 'Shipped',   Icon: Truck },
  { key: 'DELIVERED', label: 'Delivered', Icon: Home },
]

export default function OrderDetail() {
  const { orderId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const justPlaced = !!location.state?.justPlaced

  const { data: order, isLoading, isError } = useOrder(orderId)

  // Scroll to top when opening a different order.
  useEffect(() => { window.scrollTo({ top: 0 }) }, [orderId])

  if (isLoading) return <DetailSkeleton />

  if (isError || !order) {
    return (
      <Container size="lg" className="orderdetail">
        <BackLink />
        <Alert variant="danger" title="Order not found">
          <p style={{ margin: '0 0 var(--space-3)' }}>
            We couldn&apos;t find this order. It may have been removed.
          </p>
          <Button as={Link} to="/orders" size="sm">Back to my orders</Button>
        </Alert>
      </Container>
    )
  }

  const items = order.orderItems || []
  const status = order.status
  const isCancelled = status === 'CANCELLED'
  const isPaymentFailed = status === 'PAYMENT_FAILED'
  const isRefunded = status === 'REFUNDED'

  return (
    <Container size="xl" className="orderdetail">
      <BackLink />

      {justPlaced && (
        <Alert
          variant="success"
          icon={<CheckCircle2 size={20} />}
          title="Order placed successfully!"
          className="orderdetail__placed"
        >
          Thanks for your order. We&apos;ll confirm with you on WhatsApp shortly.
        </Alert>
      )}

      {/* ---- header ---- */}
      <header className="orderdetail__header">
        <div className="orderdetail__head-l">
          <h1 className="orderdetail__title">Order {shortOrderId(order.orderId)}</h1>
          <p className="orderdetail__placed-on">
            Placed on {formatOrderDate(order.orderedAt)}
          </p>
        </div>
        <Badge variant={ORDER_STATUS_VARIANT[status] || 'neutral'} size="md">
          {status}
        </Badge>
      </header>

      {/* ---- WhatsApp CTA (only when transient field is in cache) ---- */}
      {order.whatsappLink && (
        <Card padding="lg" className="orderdetail__wa">
          <div className="orderdetail__wa-body">
            <div className="orderdetail__wa-icon" aria-hidden="true">
              <MessageCircle size={26} />
            </div>
            <div>
              <h2 className="orderdetail__wa-title">Confirm on WhatsApp</h2>
              <p className="orderdetail__wa-desc">
                Send your order details to the shop to lock in the confirmation.
              </p>
            </div>
          </div>
          <Button
            as="a"
            href={order.whatsappLink}
            target="_blank"
            rel="noopener noreferrer"
            variant="primary"
            size="lg"
            leftIcon={<MessageCircle size={18} />}
            className="orderdetail__wa-btn"
          >
            Open WhatsApp
          </Button>
        </Card>
      )}

      <div className="orderdetail__grid">
        {/* ============ left: items + timeline ============ */}
        <section className="orderdetail__main">
          {/* timeline */}
          <Card padding="lg">
            <h2 className="orderdetail__section-title">Status</h2>
            {isCancelled ? (
              <div className="orderdetail__cancelled">
                <XCircle size={28} aria-hidden="true" />
                <div>
                  <strong>Order cancelled</strong>
                  <p>This order has been cancelled. Any stock has been released.</p>
                </div>
              </div>
            ) : isPaymentFailed ? (
              <div className="orderdetail__cancelled">
                <XCircle size={28} aria-hidden="true" />
                <div>
                  <strong>Payment failed</strong>
                  <p>Your payment could not be processed. Please try placing a new order.</p>
                </div>
              </div>
            ) : isRefunded ? (
              <div className="orderdetail__cancelled">
                <XCircle size={28} aria-hidden="true" />
                <div>
                  <strong>Order refunded</strong>
                  <p>This order has been refunded. Please allow a few business days for the amount to reflect.</p>
                </div>
              </div>
            ) : (
              <Timeline status={status} />
            )}
          </Card>

          {/* items */}
          <Card padding="lg">
            <h2 className="orderdetail__section-title">
              <Package size={18} aria-hidden="true" />
              Items ({items.length})
            </h2>
            <ul className="orderdetail__items">
              {items.map((it) => (
                <li className="orderdetail__item" key={it.orderItemId || it.productId}>
                  <Link
                    to={`/products/${it.productId}`}
                    className="orderdetail__item-thumb"
                    aria-label={it.productName}
                  >
                    <img
                      src={it.imageUrl || PLACEHOLDER}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
                    />
                  </Link>
                  <div className="orderdetail__item-body">
                    <Link to={`/products/${it.productId}`} className="orderdetail__item-name">
                      {it.productName}
                    </Link>
                    {it.variantLabel && (
                      <span className="orderdetail__item-variant">{it.variantLabel}</span>
                    )}
                    <span className="orderdetail__item-meta">
                      Qty {it.quantity} · ₹{Number(it.price).toLocaleString('en-IN')}
                    </span>
                  </div>
                  <PriceTag
                    amount={Number(it.price) * Number(it.quantity)}
                    size="md"
                  />
                </li>
              ))}
            </ul>
          </Card>
        </section>

        {/* ============ right: summary + addr ============ */}
        <aside className="orderdetail__side">
          <Card padding="lg">
            <h2 className="orderdetail__section-title">Total</h2>
            <div className="orderdetail__sum-line">
              <span>Subtotal</span>
              <PriceTag amount={order.totalAmount - (order.shippingFee || 0)} size="md" />
            </div>
            <div className="orderdetail__sum-line">
              <span>Shipping</span>
              {(order.shippingFee > 0)
                ? <PriceTag amount={order.shippingFee} size="md" />
                : <span className="orderdetail__free">Free</span>
              }
            </div>
            <Divider />
            <div className="orderdetail__sum-line orderdetail__sum-line--strong">
              <span>Total paid</span>
              <PriceTag amount={order.totalAmount} size="lg" />
            </div>
          </Card>

          <Card padding="lg">
            <h2 className="orderdetail__section-title">
              <MapPin size={18} aria-hidden="true" />
              Shipping to
            </h2>
            <p className="orderdetail__address">{order.shippingAddress}</p>
            <Divider />
            <p className="orderdetail__phone">
              <Phone size={14} aria-hidden="true" />
              <a href={`tel:${order.phone}`}>{order.phone}</a>
            </p>
          </Card>

          <Button
            as={Link}
            to="/orders"
            variant="ghost"
            rightIcon={<ChevronRight size={16} />}
            className="orderdetail__back-btn"
            fullWidth
          >
            View all orders
          </Button>

          <p className="orderdetail__contact">
            For order-related queries, call or WhatsApp us at{' '}
            <a href="tel:+919894014063">+91 98940 14063</a>.
          </p>
        </aside>
      </div>
    </Container>
  )

  function BackLink() {
    return (
      <button
        type="button"
        className="orderdetail__back"
        onClick={() => navigate(-1)}
      >
        <ArrowLeft size={16} /> Back
      </button>
    )
  }
}

/* =================================================================
 * Timeline - PENDING -> CONFIRMED -> SHIPPED -> DELIVERED
 * The current status is highlighted; everything before it is "done".
 * ================================================================= */
function Timeline({ status }) {
  // Razorpay-created orders start as PAYMENT_PENDING then move to PAID
  // before the admin confirms them. Normalise both to PENDING so the
  // first step ("Placed") shows as the active node.
  const normalised = (status === 'PAID' || status === 'PAYMENT_PENDING') ? 'PENDING' : status
  const currentIdx = Math.max(0, TIMELINE.findIndex((s) => s.key === normalised))
  return (
    <ol className="orderdetail__timeline" aria-label="Order progress">
      {TIMELINE.map((step, i) => {
        const done = i < currentIdx
        const active = i === currentIdx
        const Icon = step.Icon
        return (
          <li
            key={step.key}
            className={[
              'orderdetail__tl',
              done && 'is-done',
              active && 'is-active',
            ].filter(Boolean).join(' ')}
          >
            <span className="orderdetail__tl-dot" aria-hidden="true">
              <Icon size={14} />
            </span>
            <span className="orderdetail__tl-label">{step.label}</span>
            {i < TIMELINE.length - 1 && (
              <span className="orderdetail__tl-bar" aria-hidden="true" />
            )}
          </li>
        )
      })}
    </ol>
  )
}

/* =================================================================
 * Skeleton
 * ================================================================= */
function DetailSkeleton() {
  return (
    <Container size="xl" className="orderdetail">
      <Skeleton width={80} height={20} />
      <header className="orderdetail__header">
        <Skeleton width={240} height={32} />
        <Skeleton width={100} height={24} />
      </header>
      <div className="orderdetail__grid">
        <div className="orderdetail__main">
          <Skeleton height={140} />
          <Skeleton height={280} />
        </div>
        <div className="orderdetail__side">
          <Skeleton height={160} />
          <Skeleton height={160} />
        </div>
      </div>
    </Container>
  )
}
