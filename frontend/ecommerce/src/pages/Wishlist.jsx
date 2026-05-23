/**
 * Wishlist page (protected).
 *
 * Shows products the user has hearted, with the same three actions
 * available on the detail page:
 *   - Buy now    -> add to cart + go to /checkout
 *   - Add to cart
 *   - Remove from wishlist (trash icon)
 *
 * Backend returns wishlist items as
 *   { userId, productId, createdAt, product: {...catalog snapshot...} }
 * so we can render rich cards (image, rating, stock badge) without an
 * extra round-trip per product.
 */
import { Link, useNavigate } from 'react-router-dom'
import {
  Heart,
  ShoppingBag,
  Zap,
  Trash2,
  ArrowLeft,
} from 'lucide-react'

import {
  Container,
  Card,
  Button,
  IconButton,
  PriceTag,
  RatingStars,
  EmptyState,
  Skeleton,
  Alert,
  Badge,
} from '../components'
import { useWishlist, useRemoveFromWishlist } from '../lib/wishlist'
import { useAddToCart } from '../lib/cart'
import './Wishlist.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

export default function Wishlist() {
  const navigate = useNavigate()
  const { data, isLoading, isError, refetch } = useWishlist()
  const addToCart = useAddToCart()
  const remove = useRemoveFromWishlist()

  const items = Array.isArray(data) ? data : []

  /* ---------------- loading ---------------- */
  if (isLoading) {
    return (
      <Container size="xl" className="wishlist">
        <PageHeader />
        <div className="wishlist__grid">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} height={320} radius="lg" />
          ))}
        </div>
      </Container>
    )
  }

  /* ---------------- error ---------------- */
  if (isError) {
    return (
      <Container size="lg">
        <Alert
          variant="danger"
          title="Could not load your wishlist"
          action={<Button size="sm" onClick={() => refetch()}>Retry</Button>}
        >
          Please check your connection and try again.
        </Alert>
      </Container>
    )
  }

  /* ---------------- empty ---------------- */
  if (items.length === 0) {
    return (
      <Container size="lg" className="wishlist">
        <PageHeader />
        <EmptyState
          icon={<Heart size={40} />}
          title="Your wishlist is empty"
          description="Tap the heart on any product to save it here for later."
          action={
            <Button as={Link} to="/products" variant="primary" size="lg">
              Browse products
            </Button>
          }
        />
      </Container>
    )
  }

  /* ---------------- list ---------------- */
  const handleAdd = (product) => {
    if (!product) return
    addToCart.mutate({
      productId: product.id || product.productId,
      quantity: 1,
      product,
    })
  }

  const handleBuyNow = (product) => {
    if (!product) return
    const pid = product.id || product.productId
    if (!pid) return
    // Skip cart entirely - Checkout will post a single-item order
    // (see useCheckout `item` branch) so the user's saved cart stays
    // intact even though they used Buy Now from the wishlist.
    navigate('/checkout', {
      state: {
        buyNow: {
          productId: pid,
          variantId: null,
          quantity: 1,
          snapshot: {
            name: product.name,
            price: Number(product.price) || 0,
            imageUrl: product.primaryImageUrl
              || product.imageUrl
              || (Array.isArray(product.images) ? product.images[0]?.url : null)
              || null,
          },
        },
      },
    })
  }

  return (
    <Container size="xl" className="wishlist">
      <PageHeader itemCount={items.length} />

      <div className="wishlist__grid">
        {items.map((item) => (
          <WishlistTile
            key={item.productId}
            item={item}
            onAdd={handleAdd}
            onBuyNow={handleBuyNow}
            onRemove={() => remove.mutate({ productId: item.productId })}
            isAddingToCart={addToCart.isPending}
          />
        ))}
      </div>
    </Container>
  )
}

/* ------------------------------------------------------------------ */

function PageHeader({ itemCount }) {
  return (
    <header className="wishlist__header">
      <Link to="/products" className="wishlist__back">
        <ArrowLeft size={14} aria-hidden="true" />
        <span>Continue shopping</span>
      </Link>
      <h1 className="wishlist__title">
        My wishlist
        {itemCount != null && (
          <span className="wishlist__count">
            {itemCount} item{itemCount === 1 ? '' : 's'}
          </span>
        )}
      </h1>
    </header>
  )
}

function WishlistTile({ item, onAdd, onBuyNow, onRemove, isAddingToCart }) {
  const product = item.product || {}
  const productId = product.id || product.productId || item.productId
  const outOfStock = product.stockQuantity != null && product.stockQuantity <= 0
  const rating = product.averageRating
  const reviews = product.reviewCount

  return (
    <Card padding="none" className="wl-tile">
      <Link to={`/products/${productId}`} className="wl-tile__media">
        <img
          src={product.primaryImageUrl || PLACEHOLDER}
          alt={product.name || 'Product'}
          loading="lazy"
          onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
        />
        {outOfStock && (
          <div className="wl-tile__overlay">
            <Badge variant="danger">Out of stock</Badge>
          </div>
        )}
        <IconButton
          size="sm"
          variant="ghost"
          aria-label="Remove from wishlist"
          title="Remove from wishlist"
          className="wl-tile__remove"
          onClick={(e) => {
            e.preventDefault()
            e.stopPropagation()
            onRemove()
          }}
        >
          <Trash2 size={16} />
        </IconButton>
      </Link>

      <div className="wl-tile__body">
        <Link to={`/products/${productId}`} className="wl-tile__name">
          {product.name || 'Product'}
        </Link>

        {(rating != null || reviews != null) && (
          <RatingStars size="sm" value={rating || 0} reviewCount={reviews} />
        )}

        <div className="wl-tile__price">
          <PriceTag amount={product.price || 0} size="md" />
        </div>

        <div className="wl-tile__actions">
          <Button
            size="sm"
            variant="secondary"
            leftIcon={<ShoppingBag size={16} />}
            disabled={outOfStock || isAddingToCart}
            onClick={() => onAdd(product)}
            className="wl-tile__add"
          >
            {outOfStock ? 'Sold out' : 'Add to cart'}
          </Button>
          <Button
            size="sm"
            variant="primary"
            leftIcon={<Zap size={16} />}
            disabled={outOfStock || isAddingToCart}
            onClick={() => onBuyNow(product)}
            className="wl-tile__buy"
          >
            Buy now
          </Button>
        </div>
      </div>
    </Card>
  )
}
