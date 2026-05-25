/**
 * Product detail page.
 *
 * Backend contract:
 *   GET /api/product?productId=<uuid>              -> single Product (with
 *                                                     averageRating, reviewCount,
 *                                                     reviews[], images[]).
 *   GET /api/product?productId=<uuid>&related=true -> recommendation rail.
 *
 * Ingredients come from the backend as a single TEXT column. We
 * split it into list items on newlines / commas / semicolons so it
 * renders as a tidy bulleted list regardless of how the admin
 * formatted the field.
 */
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  ShoppingBag,
  ShieldCheck,
  Truck,
  Leaf,
  ArrowLeft,
  Sparkles,
  Share2,
  Zap,
  MailWarning,
  Loader2,
} from 'lucide-react'

import {
  Container,
  Button,
  Badge,
  Tag,
  PriceTag,
  RatingStars,
  ReviewCard,
  QuantityStepper,
  Skeleton,
  Alert,
  EmptyState,
  ProductCard,
  Divider,
  ShareModal,
  WishlistButton,
} from '../components'
import { useProduct, useRelatedProducts } from '../lib/products'
import { useAddToCart } from '../lib/cart'
import { useSubmitReview } from '../lib/reviews'
import { useMyProfile } from '../lib/me'
import { useAuthStore } from '../stores/authStore'
import Seo from '../components/Seo'
import { productLd, breadcrumbLd } from '../lib/seo'
import './ProductDetail.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

/* Map a backend Review (comment / userEmail) onto the shape
 * ReviewCard expects (body / user.name). */
function toReviewCardShape(r) {
  const email = r?.userEmail || ''
  const localPart = email.includes('@') ? email.split('@')[0] : email
  return {
    reviewId: r.reviewId,
    rating: r.rating,
    body: r.comment,
    createdAt: r.createdAt,
    user: { name: localPart || 'Customer' },
    verifiedPurchase: false,
  }
}

/* Display-friendly category label. Source data may be camelCase
 * ("testCat"), kebab-case ("south-indian") or already nice -- we
 * split on case/sep boundaries and title-case each word. */
function formatCategory(raw) {
  if (!raw) return ''
  const spaced = String(raw)
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
  return spaced
    .split(/\s+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

/* The backend stores ingredients as free-form TEXT. Admins can
 * separate items by newlines, commas, semicolons or bullets - we
 * normalize all of those to a clean string[] so the panel always
 * renders as a tidy list. Returns [] when the column is empty or
 * the input isn't a string (defensive against older array data). */
function parseIngredients(raw) {
  if (Array.isArray(raw)) {
    return raw.map((s) => String(s).trim()).filter(Boolean)
  }
  if (typeof raw !== 'string') return []
  const cleaned = raw.trim()
  if (!cleaned) return []
  return cleaned
    .split(/\r?\n|[,;•·]/)
    .map((s) => s.trim().replace(/^[-*\s]+/, ''))
    .filter(Boolean)
}

export default function ProductDetail() {
  const { productId } = useParams()
  const { data: product, isLoading, isError, refetch } = useProduct(productId)
  const { data: related } = useRelatedProducts(productId)
  const sameCategory = related?.sameCategory || []
  const alsoBought   = related?.alsoBought   || []
  const addToCart = useAddToCart()
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const navigate = useNavigate()

  // Scroll to top whenever the product changes (e.g. via a rec card).
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' })
  }, [productId])

  const images = useMemo(() => {
    if (!product) return []
    const arr = Array.isArray(product.images) ? product.images : []
    const urls = arr.map((i) => i.imageUrl || i.url).filter(Boolean)
    if (urls.length) return urls
    return product.primaryImageUrl ? [product.primaryImageUrl] : []
  }, [product])

  const [activeImg, setActiveImg] = useState(0)
  useEffect(() => { setActiveImg(0) }, [productId])

  const [qty, setQty] = useState(1)
  useEffect(() => { setQty(1) }, [productId])

  /* ---------------- variant picker ----------------
   * Variants are optional. When a product has them, we pick the first
   * in-stock active variant as the default (falling back to the first
   * variant if all happen to be sold out, so the picker still renders).
   * The selected variant becomes the source of truth for price, stock
   * badges and add-to-cart - the product-level fields are only used
   * when `variants` is empty (single-size product).
   */
  const variants = useMemo(() => {
    return Array.isArray(product?.variants) ? product.variants : []
  }, [product])
  const hasVariants = variants.length > 0

  const [selectedVariantId, setSelectedVariantId] = useState(null)
  useEffect(() => {
    if (!hasVariants) {
      setSelectedVariantId(null)
      return
    }
    const firstInStock = variants.find(
      (v) => v.active !== false && (v.stockQuantity ?? 0) > 0,
    )
    setSelectedVariantId((firstInStock || variants[0]).variantId)
  }, [productId, hasVariants, variants])

  const selectedVariant = hasVariants
    ? variants.find((v) => v.variantId === selectedVariantId) || null
    : null

  // Effective price/stock - variant when one is picked, else product-level.
  const effectivePrice = selectedVariant ? selectedVariant.price : product?.price
  const effectiveStock = selectedVariant
    ? selectedVariant.stockQuantity
    : product?.stockQuantity

  // Clamp the quantity stepper whenever the user switches to a variant
  // with a smaller stock (e.g. picking 250g (40) then 1 kg (10) when
  // they had qty 15 - drop to 10 instead of letting the backend reject).
  useEffect(() => {
    if (effectiveStock != null && qty > effectiveStock) {
      setQty(Math.max(1, effectiveStock))
    }
  }, [effectiveStock, qty])

  // Share dialog
  const [shareOpen, setShareOpen] = useState(false)

  /* ---------------- loading / error ---------------- */
  if (isLoading) return <ProductDetailSkeleton />
  if (isError || !product) {
    return (
      <Container size="xl" className="pd">
        <Alert
          variant="danger"
          title="Could not load this product"
          action={<Button size="sm" onClick={() => refetch()}>Retry</Button>}
        >
          Please check your connection and try again.
        </Alert>
        <div style={{ marginTop: 'var(--space-6)' }}>
          <Button variant="ghost" as={Link} to="/products" leftIcon={<ArrowLeft size={16} />}>
            Back to products
          </Button>
        </div>
      </Container>
    )
  }

  const outOfStock = effectiveStock != null && effectiveStock <= 0
  const lowStock = !outOfStock && effectiveStock != null && effectiveStock <= 5
  const ingredients = parseIngredients(product.ingredients)

  const handleAdd = () => {
    if (outOfStock) return
    if (!isAuthed) {
      navigate('/login', { state: { from: { pathname: `/products/${productId}` } } })
      return
    }
    addToCart.mutate({
      productId: product.id || product.productId,
      variantId: selectedVariant ? selectedVariant.variantId : null,
      quantity: qty,
      product,
    })
  }

  /* Buy Now: skip the cart entirely. We hand a small product snapshot
   * to the Checkout page via router state so it can render the order
   * summary, and the actual order is placed against POST /api/order
   * with an `item` payload that the backend treats as a one-off
   * single-product checkout (cart is NOT touched, in case the user has
   * other things saved there). */
  const handleBuyNow = () => {
    if (outOfStock) return
    if (!isAuthed) {
      navigate('/login', { state: { from: { pathname: `/products/${productId}` } } })
      return
    }
    const pid = product.id || product.productId
    const unitPrice = selectedVariant
      ? Number(selectedVariant.price ?? product.price)
      : Number(product.price)
    navigate('/checkout', {
      state: {
        buyNow: {
          productId: pid,
          variantId: selectedVariant ? selectedVariant.variantId : null,
          quantity: qty,
          snapshot: {
            name: selectedVariant?.label
              ? `${product.name} (${selectedVariant.label})`
              : product.name,
            price: unitPrice,
            imageUrl: images[0] || null,
          },
        },
      },
    })
  }

  return (
    <div className="pd">
      <Seo
        title={`${product.name} - buy online`}
        description={(product.description || '').slice(0, 158) || `Buy ${product.name} online from Arusuvai Junction.`}
        path={`/products/${product.id ?? product.productId}`}
        image={product.primaryImageUrl || (Array.isArray(product.images) && (product.images[0]?.imageUrl || product.images[0]?.url))}
        type="product"
        jsonLd={[
          productLd(product, `/products/${product.id ?? product.productId}`),
          breadcrumbLd([
            { name: 'Home', path: '/' },
            { name: 'Products', path: '/products' },
            { name: product.name, path: `/products/${product.id ?? product.productId}` },
          ]),
        ]}
      />
      <Container size="xl">
        {/* ---------- back link ---------- */}
        <nav className="pd__crumbs" aria-label="Breadcrumb">
          <Link to="/products" className="pd__back">
            <ArrowLeft size={14} aria-hidden="true" />
            <span>Back to products</span>
          </Link>
        </nav>

        {/* ---------- hero ---------- */}
        <section className="pd__hero">
          {/* Gallery */}
          <div className="pd__gallery">
            <div className="pd__main-img">
              <img
                src={images[activeImg] || PLACEHOLDER}
                alt={product.name}
                fetchpriority="high"
                decoding="async"
                onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
              />
              {outOfStock && (
                <div className="pd__overlay">
                  <Badge variant="danger">Out of stock</Badge>
                </div>
              )}
            </div>
            {images.length > 1 && (
              <div className="pd__thumbs" role="tablist" aria-label="Product images">
                {images.map((src, i) => (
                  <button
                    key={i}
                    type="button"
                    role="tab"
                    aria-selected={i === activeImg}
                    className={`pd__thumb ${i === activeImg ? 'is-active' : ''}`}
                    onClick={() => setActiveImg(i)}
                  >
                    <img src={src} alt="" loading="lazy" decoding="async" onError={(e) => { e.currentTarget.src = PLACEHOLDER }} />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Info */}
          <div className="pd__info">
            {product.category && (
              <Tag className="pd__category">{formatCategory(product.category)}</Tag>
            )}
            <h1 className="pd__name">{product.name}</h1>
            {product.nameTamil && (
              <p className="pd__name-tamil" lang="ta">{product.nameTamil}</p>
            )}

            <div className="pd__rating-row">
              <RatingStars value={product.averageRating || 0} size="md" />
              <span className="pd__rating-meta">
                {product.averageRating != null
                  ? `${Number(product.averageRating).toFixed(1)} · ${product.reviewCount || 0} review${product.reviewCount === 1 ? '' : 's'}`
                  : 'No reviews yet'}
              </span>
            </div>

            <div className="pd__price-row">
              <PriceTag amount={(effectivePrice ?? 0) * qty} size="lg" />
              {qty > 1 && (
                <span className="pd__price-each">
                  ₹{Number(effectivePrice).toLocaleString('en-IN')} × {qty}
                </span>
              )}
              {outOfStock ? (
                <Badge variant="danger">Out of stock</Badge>
              ) : lowStock ? (
                <Badge variant="warning">Only {effectiveStock} left</Badge>
              ) : (
                <Badge variant="success">In stock</Badge>
              )}
            </div>

            {/* The full description renders below in the
                "About this product" panel - no duplicate here. */}

            {hasVariants && (
              <div className="pd__variants" role="radiogroup" aria-label="Select size">
                <span className="pd__variants-label">Size</span>
                <div className="pd__variant-chips">
                  {variants.map((v) => {
                    const isSelected = v.variantId === selectedVariantId
                    const isSoldOut = (v.stockQuantity ?? 0) <= 0
                    return (
                      <button
                        key={v.variantId}
                        type="button"
                        role="radio"
                        aria-checked={isSelected}
                        disabled={isSoldOut}
                        onClick={() => setSelectedVariantId(v.variantId)}
                        className={
                          'pd__variant-chip' +
                          (isSelected ? ' is-selected' : '') +
                          (isSoldOut ? ' is-disabled' : '')
                        }
                      >
                        <span className="pd__variant-label">{v.label}</span>
                        {isSoldOut && (
                          <span className="pd__variant-meta">Sold out</span>
                        )}
                      </button>
                    )
                  })}
                </div>
              </div>
            )}

            <Divider />

            <div className="pd__cart">
              <QuantityStepper
                value={qty}
                onChange={setQty}
                min={1}
                max={Math.max(1, Math.min(effectiveStock ?? 99, 99))}
                disabled={outOfStock}
              />
              <Button
                size="lg"
                variant="secondary"
                leftIcon={<ShoppingBag size={18} />}
                disabled={outOfStock}
                loading={addToCart.isPending}
                onClick={handleAdd}
                className="pd__add"
              >
                {outOfStock ? 'Sold out' : 'Add to cart'}
              </Button>
              <Button
                size="lg"
                variant="primary"
                leftIcon={<Zap size={18} />}
                disabled={outOfStock}
                loading={addToCart.isPending}
                onClick={handleBuyNow}
                className="pd__buy"
              >
                Buy now
              </Button>
              <WishlistButton
                product={product}
                variant="inline"
                stopPropagation={false}
                className="pd__wish"
              />
              <Button
                size="lg"
                variant="secondary"
                leftIcon={<Share2 size={18} />}
                onClick={() => setShareOpen(true)}
                className="pd__share"
                aria-label={`Share ${product.name}`}
              >
                Share
              </Button>
            </div>

            <ul className="pd__perks">
              <li><Truck size={16} /> Freshly packed &amp; shipped within 24 hours</li>
              <li><ShieldCheck size={16} /> Quality assured · No preservatives</li>
              <li><Leaf size={16} /> Made with traditional Tamil recipes</li>
            </ul>
          </div>
        </section>

        {/* ---------- details sections ---------- */}
        <section className="pd__panels" aria-label="Product details">
          {product.description && (
            <article className="pd__panel">
              <h2 className="pd__panel-title">About this product</h2>
              <p className="pd__panel-body">{product.description}</p>
              {product.descriptionTamil && (
                <p className="pd__panel-body pd__panel-body--tamil" lang="ta">
                  {product.descriptionTamil}
                </p>
              )}
            </article>
          )}

          {/* Ingredients (parsed from a free-text column on the backend) */}
          <article className="pd__panel">
            <div className="pd__panel-head">
              <h2 className="pd__panel-title">Ingredients</h2>
              {ingredients.length === 0 && (
                <Badge variant="warning" size="sm">Coming soon</Badge>
              )}
            </div>
            {ingredients.length > 0 ? (
              <ul className="pd__ingredients">
                {ingredients.map((ing, i) => (
                  <li key={i}><Sparkles size={14} aria-hidden="true" /> {ing}</li>
                ))}
              </ul>
            ) : (
              <p className="pd__panel-body pd__panel-body--muted">
                We&apos;re working on listing the full ingredient breakdown
                here so you can shop with confidence. Check back soon.
              </p>
            )}
          </article>
        </section>

        {/* ---------- recommendations ---------- */}
        {alsoBought.length > 0 && (
          <section className="pd__related" aria-label="Customers also bought">
            <div className="pd__section-head">
              <h2 className="pd__panel-title">Customers also bought</h2>
            </div>
            <div className="pd__related-grid">
              {alsoBought.map((p) => (
                <ProductCard
                  key={p.productId || p.id}
                  product={{ ...p, productId: p.productId || p.id }}
                />
              ))}
            </div>
          </section>
        )}

        {sameCategory.length > 0 && (
          <section className="pd__related" aria-label="Similar products">
            <div className="pd__section-head">
              <h2 className="pd__panel-title">
                {product.category
                  ? `More in ${formatCategory(product.category)}`
                  : 'Similar products'}
              </h2>
              {product.category && (
                <Link
                  to={`/products?category=${encodeURIComponent(product.category.toLowerCase())}`}
                  className="pd__see-all"
                >
                  See all →
                </Link>
              )}
            </div>
            <div className="pd__related-grid">
              {sameCategory.map((p) => (
                <ProductCard
                  key={p.productId || p.id}
                  product={{ ...p, productId: p.productId || p.id }}
                />
              ))}
            </div>
          </section>
        )}

        {/* ---------- reviews (kept at the bottom so shoppers see
             product info + related items first) ---------- */}
        <section className="pd__reviews" aria-label="Customer reviews">
          <div className="pd__section-head">
            <h2 className="pd__panel-title">Customer reviews</h2>
            {product.reviewCount > 0 && (
              <span className="pd__panel-meta">
                {Number(product.averageRating || 0).toFixed(1)} out of 5 · {product.reviewCount} review{product.reviewCount === 1 ? '' : 's'}
              </span>
            )}
          </div>

          {/* Reviews list first - shoppers want social proof before
              they're asked to contribute. */}
          {Array.isArray(product.reviews) && product.reviews.length > 0 ? (
            <div className="pd__reviews-grid">
              {product.reviews.map((r) => (
                <ReviewCard key={r.reviewId} review={toReviewCardShape(r)} />
              ))}
            </div>
          ) : (
            <EmptyState
              title="No reviews yet"
              description="Be the first to share your thoughts after trying this product."
            />
          )}

          {/* Composer below the list. */}
          <ReviewComposer productId={product.id || product.productId} />
        </section>
      </Container>

      <ShareModal
        open={shareOpen}
        onClose={() => setShareOpen(false)}
        url={typeof window !== 'undefined' ? window.location.href : ''}
        title={product.name}
        text={
          effectivePrice != null
            ? `Check out ${product.name} on Arusuvai`
            : `Check out ${product.name} on Arusuvai`
        }
      />
    </div>
  )
}

/* ---------------- review composer ----------------
 * Inline review form shown above the reviews list.
 *
 * Visibility rules (mirror the backend gates):
 *   - guest                          -> "Sign in to leave a review" CTA
 *   - signed-in, email NOT verified  -> verification prompt + link to /account
 *   - signed-in, email verified      -> star picker + comment field + submit
 *
 * The backend (POST /api/review) is a true upsert: posting again
 * overwrites the caller's previous review for the same product.
 */
function ReviewComposer({ productId }) {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const navigate = useNavigate()
  const { data: profile } = useMyProfile()
  const submit = useSubmitReview(productId)

  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')

  if (!isAuthed) {
    return (
      <div className="pd__review-composer pd__review-composer--prompt">
        <p>Want to share your experience?</p>
        <Button
          size="sm"
          onClick={() =>
            navigate('/login', {
              state: { from: { pathname: `/products/${productId}` } },
            })
          }
        >
          Sign in to leave a review
        </Button>
      </div>
    )
  }

  // /api/me hasn't resolved yet - render nothing rather than flicker
  // the wrong CTA. (The reviews list still renders above/below.)
  if (!profile) {
    return null
  }

  if (!profile.emailVerified) {
    return (
      <div className="pd__review-composer pd__review-composer--prompt">
        <div className="pd__review-warn">
          <MailWarning size={18} aria-hidden="true" />
          <div>
            <p style={{ margin: 0, fontWeight: 600 }}>
              Verify your email to leave a review
            </p>
            <p style={{ margin: '.25rem 0 0', fontSize: '.85rem', opacity: 0.8 }}>
              We sent a verification link to <strong>{profile.email}</strong>.
              You can resend it from your account page.
            </p>
          </div>
        </div>
        <Button size="sm" variant="ghost" onClick={() => navigate('/account')}>
          Go to account
        </Button>
      </div>
    )
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (rating < 1) {
      toast.error('Please pick a star rating')
      return
    }
    try {
      await submit.mutateAsync({ rating, comment: comment.trim() })
      toast.success('Thanks for your review!')
      setRating(0)
      setComment('')
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.message ||
        'Could not submit review'
      toast.error(msg)
    }
  }

  return (
    <form className="pd__review-composer" onSubmit={handleSubmit}>
      <h3 className="pd__review-composer-title">Share your experience</h3>
      <div className="pd__review-composer-row">
        <span className="pd__review-composer-label">Your rating</span>
        <RatingStars value={rating} onChange={setRating} editable size={22} />
      </div>
      <label className="pd__review-composer-row pd__review-composer-row--col">
        <span className="pd__review-composer-label">Your review (optional)</span>
        <textarea
          className="pd__review-textarea"
          rows={3}
          maxLength={1000}
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="What did you like? How was the freshness, taste, packaging?"
        />
      </label>
      <div className="pd__review-composer-actions">
        <Button type="submit" size="sm" disabled={submit.isPending || rating < 1}>
          {submit.isPending && <Loader2 size={14} className="spin" aria-hidden="true" />}
          Post review
        </Button>
      </div>
    </form>
  )
}

/* ---------------- skeleton ---------------- */
function ProductDetailSkeleton() {
  return (
    <div className="pd">
      <Container size="xl">
        <div className="pd__crumbs">
          <Skeleton width={180} height={14} />
        </div>
        <section className="pd__hero">
          <div className="pd__gallery">
            <Skeleton
              width="100%"
              height={0}
              style={{ aspectRatio: '1 / 1', borderRadius: 'var(--radius-lg)' }}
            />
          </div>
          <div className="pd__info">
            <Skeleton width={100} height={22} />
            <Skeleton width="80%" height={32} />
            <Skeleton width="40%" height={20} />
            <Skeleton width="30%" height={28} />
            <Skeleton width="100%" height={60} />
            <Skeleton width="100%" height={48} />
          </div>
        </section>
      </Container>
    </div>
  )
}
