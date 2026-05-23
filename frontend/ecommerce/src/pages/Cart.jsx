/**
 * Cart page (protected).
 *
 * Backend contract: see src/lib/cart.js. Cart shape arrives with
 * per-line subtotals and a server-computed totalAmount.
 *
 * Layout:
 *   - left:  scrollable item list (image, name, price, qty, subtotal, remove)
 *   - right: sticky order summary (subtotal, shipping, total, checkout)
 *
 * Mutations are optimistic so the UI feels instant; the server
 * response is the source of truth and overwrites the optimistic cart
 * on settle.
 */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ShoppingBag,
  Trash2,
  ArrowLeft,
  ShieldCheck,
  Truck,
  Tag as TagIcon,
} from 'lucide-react'

import {
  Container,
  Card,
  Button,
  IconButton,
  PriceTag,
  QuantityStepper,
  EmptyState,
  Skeleton,
  Alert,
  Divider,
} from '../components'
import {
  useCart,
  useUpdateCartItem,
  useRemoveCartItem,
  useClearCart,
} from '../lib/cart'
import './Cart.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

export default function Cart() {
  const { data, isLoading, isError, refetch } = useCart()
  const updateItem = useUpdateCartItem()
  const removeItem = useRemoveCartItem()
  const clearCart = useClearCart()

  const items = data?.cartItems || []
  const subtotal = data?.totalAmount ?? items.reduce(
    (s, it) => s + (it.subtotal ?? it.price * it.quantity),
    0,
  )
  const shipping = items.length > 0 ? 0 : 0   // free shipping for now
  const total = subtotal + shipping

  /* ---------------- loading / error ---------------- */
  if (isLoading) return <CartSkeleton />
  if (isError) {
    return (
      <Container size="lg">
        <Alert
          variant="danger"
          title="Could not load your cart"
          action={<Button size="sm" onClick={() => refetch()}>Retry</Button>}
        >
          Please check your connection and try again.
        </Alert>
      </Container>
    )
  }

  /* ---------------- empty state ---------------- */
  if (items.length === 0) {
    return (
      <Container size="lg" className="cart">
        <PageHeader />
        <EmptyState
          icon={<ShoppingBag size={40} />}
          title="Your cart is empty"
          description="Browse our handmade snacks and sweets to get started."
          action={
            <Button as={Link} to="/products" variant="primary" size="lg">
              Shop now
            </Button>
          }
        />
      </Container>
    )
  }

  return (
    <Container size="xl" className="cart">
      <PageHeader itemCount={items.length} />

      <div className="cart__layout">
        {/* -------- items column -------- */}
        <section className="cart__items" aria-label="Cart items">
          {items.map((it) => (
            <CartRow
              key={it.cartItemId || `${it.productId}-${it.variantId || 'base'}`}
              item={it}
              onQtyChange={(q) =>
                updateItem.mutate({
                  productId: it.productId,
                  variantId: it.variantId || null,
                  quantity: q,
                })
              }
              onRemove={() =>
                removeItem.mutate({
                  productId: it.productId,
                  variantId: it.variantId || null,
                })
              }
              busy={updateItem.isPending || removeItem.isPending}
            />
          ))}

          <div className="cart__actions">
            <Button as={Link} to="/products" variant="ghost" leftIcon={<ArrowLeft size={16} />}>
              Continue shopping
            </Button>
            <ClearCartButton onClear={() => clearCart.mutate()} loading={clearCart.isPending} />
          </div>
        </section>

        {/* -------- summary column -------- */}
        <aside className="cart__summary" aria-label="Order summary">
          <Card padding="lg" className="cart__summary-card">
            <h2 className="cart__summary-title">Order Summary</h2>
            <SummaryLine label={`Subtotal (${items.length} item${items.length === 1 ? '' : 's'})`} value={subtotal} />
            <SummaryLine label="Shipping" value={shipping} free={shipping === 0} />
            <Divider />
            <SummaryLine label="Total" value={total} strong />

            <Button
              as={Link}
              to="/checkout"
              variant="primary"
              size="lg"
              fullWidth
              className="cart__checkout"
            >
              Proceed to checkout
            </Button>

            <ul className="cart__perks">
              <li><Truck size={14} /> Free shipping over ₹500</li>
              <li><ShieldCheck size={14} /> Secure checkout</li>
              <li><TagIcon size={14} /> Coupons applied at checkout</li>
            </ul>
          </Card>
        </aside>
      </div>
    </Container>
  )
}

/* ============================================================
 * Sub components
 * ============================================================ */

function PageHeader({ itemCount }) {
  return (
    <header className="cart__header">
      <div>
        <h1 className="cart__title">Your Cart</h1>
        {itemCount > 0 && (
          <p className="cart__subtitle">
            {itemCount} item{itemCount === 1 ? '' : 's'} in your bag
          </p>
        )}
      </div>
    </header>
  )
}

function CartRow({ item, onQtyChange, onRemove, busy }) {
  return (
    <article className="cart__row">
      <Link to={`/products/${item.productId}`} className="cart__row-img">
        <img
          src={item.imageUrl || PLACEHOLDER}
          alt={item.productName}
          onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
        />
      </Link>

      <div className="cart__row-body">
        <Link to={`/products/${item.productId}`} className="cart__row-name">
          {item.productName}
        </Link>
        {item.variantLabel && (
          <span className="cart__row-variant">{item.variantLabel}</span>
        )}
        <div className="cart__row-meta">
          <PriceTag amount={item.price} size="sm" />
          <span className="cart__row-each">each</span>
        </div>

        <div className="cart__row-controls">
          <QuantityStepper
            value={item.quantity}
            onChange={onQtyChange}
            min={1}
            max={99}
            disabled={busy}
            size="sm"
          />
          <IconButton
            aria-label="Remove from cart"
            onClick={onRemove}
            disabled={busy}
            className="cart__row-remove"
          >
            <Trash2 size={16} />
          </IconButton>
        </div>
      </div>

      <div className="cart__row-subtotal">
        <span className="cart__row-subtotal-label">Subtotal</span>
        <PriceTag
          amount={item.subtotal ?? item.price * item.quantity}
          size="md"
        />
      </div>
    </article>
  )
}

function SummaryLine({ label, value, strong, free }) {
  return (
    <div className={`cart__sum-line ${strong ? 'cart__sum-line--strong' : ''}`}>
      <span>{label}</span>
      {free ? (
        <span className="cart__sum-free">Free</span>
      ) : (
        <PriceTag amount={value} size={strong ? 'lg' : 'md'} />
      )}
    </div>
  )
}

function ClearCartButton({ onClear, loading }) {
  const [confirming, setConfirming] = useState(false)
  if (!confirming) {
    return (
      <Button
        variant="ghost"
        onClick={() => setConfirming(true)}
        leftIcon={<Trash2 size={16} />}
        disabled={loading}
      >
        Clear cart
      </Button>
    )
  }
  return (
    <div className="cart__confirm" role="group">
      <span className="cart__confirm-text">Remove all items?</span>
      <Button
        variant="danger"
        size="sm"
        onClick={() => { onClear(); setConfirming(false) }}
        loading={loading}
      >
        Yes, clear
      </Button>
      <Button variant="ghost" size="sm" onClick={() => setConfirming(false)}>
        Cancel
      </Button>
    </div>
  )
}

/* ---------------- skeleton ---------------- */
function CartSkeleton() {
  return (
    <Container size="xl" className="cart">
      <header className="cart__header">
        <Skeleton width={200} height={32} />
      </header>
      <div className="cart__layout">
        <div className="cart__items">
          {[1, 2, 3].map((i) => (
            <div className="cart__row" key={i}>
              <Skeleton width={96} height={96} style={{ borderRadius: 'var(--radius-md)' }} />
              <div className="cart__row-body">
                <Skeleton width="70%" height={20} />
                <Skeleton width="30%" height={16} />
                <Skeleton width={140} height={36} />
              </div>
              <Skeleton width={80} height={28} />
            </div>
          ))}
        </div>
        <aside className="cart__summary">
          <Card padding="lg">
            <Skeleton width="60%" height={24} />
            <div style={{ height: 'var(--space-4)' }} />
            <Skeleton width="100%" height={16} />
            <div style={{ height: 'var(--space-2)' }} />
            <Skeleton width="100%" height={16} />
            <div style={{ height: 'var(--space-4)' }} />
            <Skeleton width="100%" height={48} />
          </Card>
        </aside>
      </div>
    </Container>
  )
}
