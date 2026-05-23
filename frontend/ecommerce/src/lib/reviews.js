/* ------------------------------------------------------------------
 * Reviews data hook.
 *
 * - useHomeReviews:    dummy featured reviews for the home page.
 * - useSubmitReview:   POST /api/review (auth + verified email required).
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

/* Dummy reviews used until the backend endpoint exists. ----------- */
const DUMMY_REVIEWS = [
  {
    reviewId: 'r1',
    rating: 5,
    title: 'Tastes just like grandma made',
    body: 'The murukku is exactly like my grandmother used to make - crispy, fresh, and so authentic. Will keep ordering!',
    createdAt: '2026-04-12T10:00:00Z',
    user: {
      name: 'Priya Ramesh',
      avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=120',
    },
    verifiedPurchase: true,
  },
  {
    reviewId: 'r2',
    rating: 5,
    title: 'Quality is exceptional',
    body: "Best homemade snacks I've found online. Delivery is always on time and the packaging keeps everything fresh.",
    createdAt: '2026-03-28T08:30:00Z',
    user: {
      name: 'Karthik Kumar',
      avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=120',
    },
    verifiedPurchase: true,
  },
  {
    reviewId: 'r3',
    rating: 5,
    title: 'Authentic seedai!',
    body: 'Finally found someone who makes authentic seedai. The taste takes me right back to my childhood in Madurai.',
    createdAt: '2026-03-19T14:15:00Z',
    user: {
      name: 'Lakshmi Iyer',
      avatarUrl: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=120',
    },
    verifiedPurchase: true,
  },
  {
    reviewId: 'r4',
    rating: 4,
    title: 'Loved the mixture',
    body: 'Spice level is perfect - not too hot, not too mild. The ladoos in the same order were divine too.',
    createdAt: '2026-02-29T12:00:00Z',
    user: {
      name: 'Anand S.',
      avatarUrl: '',
    },
    verifiedPurchase: false,
  },
  {
    reviewId: 'r5',
    rating: 5,
    title: 'Diwali order was a hit',
    body: 'Ordered a festival hamper for Diwali and every relative asked where I got it from. Will be a regular customer.',
    createdAt: '2026-01-08T17:45:00Z',
    user: {
      name: 'Meena Sundaram',
      avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=120',
    },
    verifiedPurchase: true,
  },
]

/**
 * Fetch featured reviews for the home page.
 *
 * NOTE (backend swap): replace the body with a real api.get(...)
 * once the endpoint ships. The DTO shape above is already aligned
 * with what we'll expose, so the call-site code does not need to
 * change.
 */
async function fetchHomeReviews() {
  // Simulate network latency so the skeleton state is exercised.
  await new Promise((r) => setTimeout(r, 600))
  return DUMMY_REVIEWS
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
    },
  })
}
