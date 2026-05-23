/* ------------------------------------------------------------------
 * ReviewCard - displays a single user review.
 *
 * Expected review shape:
 *   {
 *     reviewId, rating (1-5), title?, body, createdAt,
 *     user: { name, avatarUrl? },
 *     verifiedPurchase?: boolean
 *   }
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import Avatar from '../Avatar/Avatar.jsx'
import RatingStars from '../RatingStars/RatingStars.jsx'
import Badge from '../Badge/Badge.jsx'
import './ReviewCard.css'

function formatDate(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return ''
  }
}

export default function ReviewCard({ review, className }) {
  if (!review) return null
  const { rating, title, body, createdAt, user, verifiedPurchase } = review
  return (
    <article className={clsx('ui-review', className)}>
      <header className="ui-review__head">
        <Avatar name={user?.name} src={user?.avatarUrl} size="sm" />
        <div className="ui-review__who">
          <div className="ui-review__name">
            {user?.name || 'Anonymous'}
            {verifiedPurchase && (
              <Badge variant="success" size="sm">Verified</Badge>
            )}
          </div>
          <div className="ui-review__meta">
            <RatingStars value={rating} size="sm" />
            <span className="ui-review__dot" aria-hidden="true">•</span>
            <time className="ui-review__date" dateTime={createdAt}>
              {formatDate(createdAt)}
            </time>
          </div>
        </div>
      </header>

      {title && <h4 className="ui-review__title">{title}</h4>}
      {body && <p className="ui-review__body">{body}</p>}
    </article>
  )
}
