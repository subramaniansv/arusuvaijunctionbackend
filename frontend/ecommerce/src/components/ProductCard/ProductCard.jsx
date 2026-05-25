/* ------------------------------------------------------------------
 * ProductCard - storefront tile.
 *
 * Expected product shape (matches backend Product DTO):
 *   {
 *     productId, name, price, mrp?, primaryImageUrl,
 *     stockQuantity, isVeg?, isOrganic?, averageRating?, reviewCount?
 *   }
 *
 * Props:
 *   product (required)
 *   onAddToCart(productId)  - if omitted, the button is hidden
 *   compact (boolean)       - smaller variant for related/recs strips
 *
 * Whole card is a Link to /products/:id. Add-to-cart button stops
 * propagation so it doesn't navigate.
 * ------------------------------------------------------------------ */
import { Link } from 'react-router-dom'
import { useState } from 'react'
import { Share2 } from 'lucide-react'
import clsx from 'clsx'
import Card from '../Card/Card.jsx'
import Badge from '../Badge/Badge.jsx'
import PriceTag from '../PriceTag/PriceTag.jsx'
import RatingStars from '../RatingStars/RatingStars.jsx'
import Button from '../Button/Button.jsx'
import ShareModal from '../ShareModal/ShareModal.jsx'
import WishlistButton from '../WishlistButton/WishlistButton.jsx'
import './ProductCard.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

export default function ProductCard({
  product,
  onAddToCart,
  compact = false,
  className,
}) {
  if (!product) return null
  const {
    productId,
    name,
    nameTamil,
    price,
    primaryImageUrl,
    stockQuantity,
    isVeg,
    isOrganic,
    averageRating,
    reviewCount,
  } = product
  const outOfStock = stockQuantity != null && stockQuantity <= 0

  const [shareOpen, setShareOpen] = useState(false)

  const handleAdd = (e) => {
    e.preventDefault()
    e.stopPropagation()
    if (!outOfStock) onAddToCart?.(productId)
  }

  const handleShare = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setShareOpen(true)
  }

  const shareUrl =
    typeof window !== 'undefined'
      ? `${window.location.origin}/products/${productId}`
      : `/products/${productId}`

  return (
    <Card
      as={Link}
      to={`/products/${productId}`}
      interactive
      padding="none"
      className={clsx('ui-product', compact && 'ui-product--compact', className)}
    >
      <div className="ui-product__media">
        <img
          src={primaryImageUrl || PLACEHOLDER}
          alt={name}
          loading="lazy"
          decoding="async"
          className="ui-product__img"
          onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
        />
        <div className="ui-product__media-badges">
          {isOrganic && <Badge variant="success">Organic</Badge>}
          {isVeg && <Badge variant="primary">Veg</Badge>}
        </div>
        <button
          type="button"
          className="ui-product__share"
          onClick={handleShare}
          aria-label={`Share ${name}`}
          title="Share"
        >
          <Share2 size={16} />
        </button>
        <WishlistButton
          product={product}
          className="ui-product__wish"
        />
        {outOfStock && (
          <div className="ui-product__overlay">
            <Badge variant="danger">Out of stock</Badge>
          </div>
        )}
      </div>

      <div className="ui-product__body">
        <h3 className="ui-product__name" title={name}>{name}</h3>
        {nameTamil && (
          <p className="ui-product__name-tamil" lang="ta" aria-hidden="true">
            {nameTamil}
          </p>
        )}

        {(averageRating != null || reviewCount != null) && (
          <RatingStars
            size="sm"
            value={averageRating || 0}
            reviewCount={reviewCount}
          />
        )}

        <div className="ui-product__footer">
          <PriceTag amount={price} size={compact ? 'sm' : 'md'} />
          {onAddToCart && (
            <Button
              size="sm"
              variant="primary"
              disabled={outOfStock}
              onClick={handleAdd}
              className="ui-product__add"
              fullWidth
            >
              {outOfStock ? 'Sold out' : 'Add to cart'}
            </Button>
          )}
        </div>
      </div>

      {shareOpen && (
        <ShareModal
          open={shareOpen}
          onClose={() => setShareOpen(false)}
          url={shareUrl}
          title={name}
          text={`Check out ${name} on Arusuvai`}
        />
      )}
    </Card>
  )
}
