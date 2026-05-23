/* ------------------------------------------------------------------
 * WishlistButton - heart toggle.
 *
 * Drop-in icon button used on product cards and the detail page.
 * Filled when the product is on the user's wishlist, outline when not.
 * For logged-out visitors the click bounces to /login with a returnTo,
 * so the heart never silently no-ops.
 *
 * Props:
 *   product            - product object (used for optimistic cache update
 *                        + figuring out productId; either `product.id` or
 *                        `product.productId` works)
 *   variant            - 'icon' (round floating, default) | 'inline'
 *                        ('inline' renders flat for use inside button rows)
 *   className
 *   stopPropagation    - default true; used when the button sits inside
 *                        a clickable card link.
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import { Heart } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

import { useToggleWishlist } from '../../lib/wishlist'
import { useAuthStore } from '../../stores/authStore'
import './WishlistButton.css'

export default function WishlistButton({
  product,
  variant = 'icon',
  className,
  stopPropagation = true,
}) {
  const navigate = useNavigate()
  const isAuthed = useAuthStore((s) => s.isAuthenticated())
  const productId = product?.id || product?.productId
  const { isInWishlist, isPending, toggle } = useToggleWishlist(productId, product)

  const handleClick = (e) => {
    if (stopPropagation) {
      e.preventDefault()
      e.stopPropagation()
    }
    if (!isAuthed) {
      navigate('/login', { state: { from: { pathname: '/wishlist' } } })
      return
    }
    toggle()
  }

  const label = isInWishlist ? 'Remove from wishlist' : 'Save to wishlist'

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={isPending}
      aria-pressed={isInWishlist}
      aria-label={label}
      title={label}
      className={clsx(
        'ui-wishlist',
        `ui-wishlist--${variant}`,
        isInWishlist && 'is-active',
        className,
      )}
    >
      <Heart
        size={variant === 'icon' ? 16 : 18}
        fill={isInWishlist ? 'currentColor' : 'none'}
        aria-hidden="true"
      />
    </button>
  )
}
