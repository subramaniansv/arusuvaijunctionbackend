/* ------------------------------------------------------------------
 * RatingStars - display or interactive 5-star rating.
 *
 * Read-only:
 *   <RatingStars value={4.5} />
 *   <RatingStars value={4.2} reviewCount={128} />
 *
 * Interactive (review form):
 *   <RatingStars value={value} onChange={setValue} editable />
 * ------------------------------------------------------------------ */
import { useState } from 'react'
import clsx from 'clsx'
import './RatingStars.css'

const TOTAL = 5

export default function RatingStars({
  value = 0,
  onChange,
  editable = false,
  size = 'md',
  reviewCount,
  showValue = false,
  className,
  ...rest
}) {
  const [hover, setHover] = useState(null)
  const displayed = hover != null ? hover : value

  return (
    <span
      className={clsx('ui-rating', `ui-rating--${size}`, editable && 'ui-rating--editable', className)}
      role={editable ? 'radiogroup' : 'img'}
      aria-label={editable ? 'Rate this product' : `Rated ${value} out of ${TOTAL}`}
      {...rest}
    >
      <span className="ui-rating__stars" aria-hidden={!editable}>
        {Array.from({ length: TOTAL }).map((_, i) => {
          const idx = i + 1
          const fillPct = Math.max(0, Math.min(1, displayed - i)) * 100
          const StarTag = editable ? 'button' : 'span'
          return (
            <StarTag
              key={idx}
              type={editable ? 'button' : undefined}
              role={editable ? 'radio' : undefined}
              aria-checked={editable ? value === idx : undefined}
              tabIndex={editable ? 0 : undefined}
              className="ui-rating__star"
              onMouseEnter={editable ? () => setHover(idx) : undefined}
              onMouseLeave={editable ? () => setHover(null) : undefined}
              onFocus={editable ? () => setHover(idx) : undefined}
              onBlur={editable ? () => setHover(null) : undefined}
              onClick={editable ? () => onChange?.(idx) : undefined}
            >
              <svg viewBox="0 0 20 20" className="ui-rating__bg" aria-hidden="true">
                <path d="M10 1.5l2.6 5.6 6.1.6-4.6 4.2 1.3 6L10 14.9l-5.4 3 1.3-6L1.3 7.7l6.1-.6L10 1.5z" />
              </svg>
              <span className="ui-rating__fill" style={{ width: `${fillPct}%` }}>
                <svg viewBox="0 0 20 20" aria-hidden="true">
                  <path d="M10 1.5l2.6 5.6 6.1.6-4.6 4.2 1.3 6L10 14.9l-5.4 3 1.3-6L1.3 7.7l6.1-.6L10 1.5z" />
                </svg>
              </span>
            </StarTag>
          )
        })}
      </span>
      {showValue && (
        <span className="ui-rating__value">{Number(value).toFixed(1)}</span>
      )}
      {reviewCount != null && (
        <span className="ui-rating__count">({reviewCount})</span>
      )}
    </span>
  )
}
