/**
 * Orders list page (protected).
 *
 * Renders the authenticated user's past orders, most-recent first
 * (backend already orders by orderedAt DESC).
 */
import { Link } from 'react-router-dom'
import { Package, ChevronRight } from 'lucide-react'

import {
  Container,
  Card,
  Button,
  Badge,
  Skeleton,
  Alert,
  EmptyState,
  PriceTag,
} from '../components'
import {
  useOrders,
  ORDER_STATUS_VARIANT,
  formatOrderDate,
  shortOrderId,
} from '../lib/orders'
import './Orders.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

export default function Orders() {
  const { data: orders = [], isLoading, isError, refetch } = useOrders()

  if (isLoading) return <OrdersSkeleton />
  if (isError) {
    return (
      <Container size="lg" className="orders">
        <PageHeader />
        <Alert variant="danger" title="Could not load your orders">
          <p style={{ margin: '0 0 var(--space-3)' }}>
            Please check your connection and try again.
          </p>
          <Button size="sm" onClick={() => refetch()}>Retry</Button>
        </Alert>
      </Container>
    )
  }

  if (orders.length === 0) {
    return (
      <Container size="lg" className="orders">
        <PageHeader />
        <EmptyState
          icon={<Package size={40} />}
          title="No orders yet"
          description="When you place an order, it'll show up here."
          action={
            <Button as={Link} to="/products" variant="primary" size="lg">
              Start shopping
            </Button>
          }
        />
      </Container>
    )
  }

  return (
    <Container size="xl" className="orders">
      <PageHeader count={orders.length} />
      <ul className="orders__list">
        {orders.map((o) => (
          <li key={o.orderId}>
            <OrderRow order={o} />
          </li>
        ))}
      </ul>
      <p className="orders__contact">
        For order-related queries, call or WhatsApp us at{' '}
        <a href="tel:+919894014063">+91 98940 14063</a>.
      </p>
    </Container>
  )
}

/* =============== sub components =============== */

function PageHeader({ count }) {
  return (
    <header className="orders__header">
      <div>
        <h1 className="orders__title">My Orders</h1>
        {count > 0 && (
          <p className="orders__sub">
            {count} order{count === 1 ? '' : 's'}
          </p>
        )}
      </div>
    </header>
  )
}

function OrderRow({ order }) {
  const items = order.orderItems || []
  const preview = items.slice(0, 4)
  const remaining = Math.max(0, items.length - preview.length)

  return (
    <Card padding="md" interactive className="orders__row" as={Link} to={`/orders/${order.orderId}`}>
      <div className="orders__row-head">
        <div className="orders__row-id">
          <span className="orders__row-id-num">{shortOrderId(order.orderId)}</span>
          <span className="orders__row-id-date">{formatOrderDate(order.orderedAt)}</span>
        </div>
        <Badge variant={ORDER_STATUS_VARIANT[order.status] || 'neutral'} size="md">
          {order.status}
        </Badge>
      </div>

      <div className="orders__row-body">
        <div className="orders__thumbs" aria-hidden="true">
          {preview.map((it, i) => (
            <span key={it.orderItemId || it.productId || i} className="orders__thumb">
              <img
                src={it.imageUrl || PLACEHOLDER}
                alt=""
                loading="lazy"
                decoding="async"
                onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
              />
            </span>
          ))}
          {remaining > 0 && (
            <span className="orders__thumb orders__thumb--more">+{remaining}</span>
          )}
        </div>

        <div className="orders__row-meta">
          <span className="orders__row-count">
            {items.length} item{items.length === 1 ? '' : 's'}
          </span>
          <PriceTag amount={order.totalAmount} size="md" />
        </div>

        <span className="orders__row-cta" aria-hidden="true">
          <ChevronRight size={18} />
        </span>
      </div>
    </Card>
  )
}

function OrdersSkeleton() {
  return (
    <Container size="xl" className="orders">
      <header className="orders__header">
        <Skeleton width={200} height={32} />
      </header>
      <ul className="orders__list">
        {[1, 2, 3].map((i) => (
          <li key={i}>
            <Card padding="md" className="orders__row">
              <div className="orders__row-head">
                <Skeleton width={140} height={20} />
                <Skeleton width={80} height={24} />
              </div>
              <div className="orders__row-body">
                <Skeleton width={200} height={48} />
                <Skeleton width={120} height={20} />
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </Container>
  )
}
