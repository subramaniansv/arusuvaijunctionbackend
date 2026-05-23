/* ------------------------------------------------------------------
 * Wishlist data hooks (React Query).
 *
 * Backend contract (WishlistController):
 *   GET    /api/wishlist                       -> WishlistItem[] (with
 *                                                 embedded Product incl.
 *                                                 primaryImageUrl + rating)
 *   GET    /api/wishlist?ids=true              -> UUID[] (productIds only,
 *                                                 used by Set lookups on
 *                                                 product cards)
 *   POST   /api/wishlist  body { productId }   -> add (idempotent)
 *   DELETE /api/wishlist?productId=<uuid>      -> remove
 *
 * All endpoints require auth. Read queries are gated on `isAuthed` so a
 * logged-out visitor never blows up with a 401 in the console.
 *
 * Mutations are optimistic against the two cache keys (full list +
 * id-set) so the heart icon flips instantly while the network is in
 * flight, with a rollback on error and a final invalidate to make the
 * server payload authoritative (catch deletions of products, etc.).
 * ------------------------------------------------------------------ */
import { useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

import { api } from './api'
import { useAuthStore } from '../stores/authStore'

const LIST_KEY = ['wishlist']
const IDS_KEY = ['wishlist', 'ids']

/* ------------------------------------------------------------------
 * Read
 * ------------------------------------------------------------------ */

/**
 * Full wishlist with embedded product cards.
 * Used by the /wishlist page.
 */
export function useWishlist() {
  const isAuthed = useAuthStore((s) => s.isAuthenticated())
  return useQuery({
    queryKey: LIST_KEY,
    enabled: isAuthed,
    queryFn: async () => {
      const res = await api.get('/api/wishlist')
      return res.data?.data || []
    },
    staleTime: 30_000,
  })
}

/**
 * ID-only fetch. Cheap, used everywhere we render product cards so
 * each card can show a filled-in heart for items already on the list.
 */
export function useWishlistIds() {
  const isAuthed = useAuthStore((s) => s.isAuthenticated())
  return useQuery({
    queryKey: IDS_KEY,
    enabled: isAuthed,
    queryFn: async () => {
      const res = await api.get('/api/wishlist', { params: { ids: true } })
      return res.data?.data || []
    },
    staleTime: 30_000,
  })
}

/** Sugar: `useIsInWishlist(productId)` -> boolean. */
export function useIsInWishlist(productId) {
  const { data: ids } = useWishlistIds()
  return useMemo(
    () => Array.isArray(ids) && !!productId && ids.includes(productId),
    [ids, productId],
  )
}

/* ------------------------------------------------------------------
 * Mutations
 * ------------------------------------------------------------------ */

function setBoth(qc, listUpdater, idsUpdater) {
  qc.setQueryData(LIST_KEY, (prev) => listUpdater(prev))
  qc.setQueryData(IDS_KEY, (prev) => idsUpdater(prev))
}

export function useAddToWishlist() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId }) => {
      const res = await api.post('/api/wishlist', { productId })
      return res.data?.data
    },
    onMutate: async ({ productId, product }) => {
      await qc.cancelQueries({ queryKey: LIST_KEY })
      await qc.cancelQueries({ queryKey: IDS_KEY })
      const prevList = qc.getQueryData(LIST_KEY)
      const prevIds = qc.getQueryData(IDS_KEY)
      setBoth(
        qc,
        (list) => {
          const items = Array.isArray(list) ? list : []
          if (items.some((it) => it.productId === productId)) return items
          // Optimistic row using whatever product snapshot the caller
          // passed in (the heart often fires from a product card so we
          // already have the data).
          return [
            {
              userId: null,
              productId,
              createdAt: Date.now(),
              product: product || { id: productId },
            },
            ...items,
          ]
        },
        (ids) => {
          const arr = Array.isArray(ids) ? ids : []
          return arr.includes(productId) ? arr : [productId, ...arr]
        },
      )
      return { prevList, prevIds }
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prevList !== undefined) qc.setQueryData(LIST_KEY, ctx.prevList)
      if (ctx?.prevIds !== undefined) qc.setQueryData(IDS_KEY, ctx.prevIds)
      toast.error(err?.response?.data?.message || 'Could not add to wishlist')
    },
    onSuccess: (list) => {
      // Server returns the authoritative list - keep it.
      if (Array.isArray(list)) {
        qc.setQueryData(LIST_KEY, list)
        qc.setQueryData(IDS_KEY, list.map((it) => it.productId))
      }
      toast.success('Saved to wishlist')
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: LIST_KEY })
      qc.invalidateQueries({ queryKey: IDS_KEY })
    },
  })
}

export function useRemoveFromWishlist() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId }) => {
      const res = await api.delete('/api/wishlist', { params: { productId } })
      return res.data?.data
    },
    onMutate: async ({ productId }) => {
      await qc.cancelQueries({ queryKey: LIST_KEY })
      await qc.cancelQueries({ queryKey: IDS_KEY })
      const prevList = qc.getQueryData(LIST_KEY)
      const prevIds = qc.getQueryData(IDS_KEY)
      setBoth(
        qc,
        (list) => (Array.isArray(list) ? list.filter((it) => it.productId !== productId) : []),
        (ids) => (Array.isArray(ids) ? ids.filter((id) => id !== productId) : []),
      )
      return { prevList, prevIds }
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.prevList !== undefined) qc.setQueryData(LIST_KEY, ctx.prevList)
      if (ctx?.prevIds !== undefined) qc.setQueryData(IDS_KEY, ctx.prevIds)
      toast.error(err?.response?.data?.message || 'Could not remove from wishlist')
    },
    onSuccess: (list) => {
      if (Array.isArray(list)) {
        qc.setQueryData(LIST_KEY, list)
        qc.setQueryData(IDS_KEY, list.map((it) => it.productId))
      }
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: LIST_KEY })
      qc.invalidateQueries({ queryKey: IDS_KEY })
    },
  })
}

/**
 * Convenience toggle: add when off, remove when on.
 * Returns a single mutation-like object exposing { toggle, isPending }.
 */
export function useToggleWishlist(productId, product) {
  const isIn = useIsInWishlist(productId)
  const add = useAddToWishlist()
  const remove = useRemoveFromWishlist()
  return {
    isInWishlist: isIn,
    isPending: add.isPending || remove.isPending,
    toggle: () => {
      if (!productId) return
      if (isIn) remove.mutate({ productId })
      else add.mutate({ productId, product })
    },
  }
}
