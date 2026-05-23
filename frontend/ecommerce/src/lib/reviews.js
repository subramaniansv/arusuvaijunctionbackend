/* ------------------------------------------------------------------
 * Reviews data hook.
 *
 * - useHomeReviews:    GET /api/review?featured=true&limit=12
 *                      (recent 4-5 star reviews with non-empty comment,
 *                       across all products — testimonial carousel).
 * - useSubmitReview:   POST /api/review (auth + verified email required).
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

/**
 * Turn an email like "priya.ramesh@gmail.com" into a privacy-respecting
 * display name "Priya R.". Falls back to "Customer" if email is missing.
 */
function emailToDisplayName(email) {
  if (!email || typeof email !== 'string') return 'Customer'
  const local = email.split('@')[0] || ''
  if (!local) return 'Customer'
  const parts = local
    .replace(/[._-]+/g, ' ')
    .replace(/\d+/g, '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  if (parts.length === 0) return 'Customer'
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()
  if (parts.length === 1) return cap(parts[0])
  return `${cap(parts[0])} ${parts[1].charAt(0).toUpperCase()}.`
}

/**
 * Adapt the backend Review DTO to the shape ReviewCard expects.
 * Backend: { reviewId, userId, productId, rating, comment, createdAt,
 *            userEmail, productName }
 * UI:      { reviewId, rating, title, body, createdAt, user, verifiedPurchase }
 */
function adaptReview(r) {
  return {
    reviewId: r.reviewId,
    productId: r.productId,
    productName: r.productName || null,
    rating: r.rating,
    title: r.productName ? `On ${r.productName}` : '',
    body: r.comment || '',
    createdAt: r.createdAt,
    user: {
      name: emailToDisplayName(r.userEmail),
      avatarUrl: '',
    },
    verifiedPurchase: true,
  }
}

async function fetchHomeReviews() {
  const res = await api.get('/api/review', {
    params: { featured: 'true', limit: 12 },
  })
  const list = res.data?.data?.reviews ?? []
  return list.map(adaptReview)
}

export function useHomeReviews() {
  return useQuery({
    queryKey: ['reviews', 'home'],
    queryFn: fetchHomeReviews,
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Create or update the caller's review for a product.
 * Backend: POST /api/review  { productId, rating, comment }
 * Requires the caller's email to be verified — the backend returns
 * 400 with a friendly message otherwise.
 *
 * On success we invalidate the product detail cache so the new
 * review and the updated averageRating/reviewCount appear on the
 * page without a manual refresh.
 */
export function useSubmitReview(productId) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ rating, comment }) => {
      const res = await api.post('/api/review', {
        productId,
        rating,
        comment: comment || null,
      })
      return res.data?.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['product', productId] })
      qc.invalidateQueries({ queryKey: ['reviews', 'home'] })
    },
  })
}

