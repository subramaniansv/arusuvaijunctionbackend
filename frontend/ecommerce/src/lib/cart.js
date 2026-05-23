/* ------------------------------------------------------------------
 * Cart data hooks (React Query).
 *
 * Backend contract (CartController):
 *   GET    /api/cart                       -> Cart
 *   POST   /api/cart   body { productId, quantity }   -> add (or +qty)
 *   PUT    /api/cart   body { productId, quantity }   -> set qty
 *   DELETE /api/cart?productId=<uuid>      -> remove one item
 *   DELETE /api/cart                        -> clear cart
 *
 * Cart shape:
 *   { cartId, userId, totalAmount,
 *     cartItems: [{ cartItemId, productId, productName,
 *                   imageUrl, price, quantity, subtotal }] }
 *
 * All endpoints require auth. We gate the GET query on isAuthed so a
 * logged-out visitor never sees a phantom 401 in the console.
 *
 * Mutations: optimistic updates against the ['cart'] cache for snappy
 * UX, with a rollback on error and a final invalidate so server-side
 * truths (totalAmount rounding, stock clamping, etc.) win.
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

import { api } from './api'
import { useAuthStore } from '../stores/authStore'

const CART_KEY = ['cart']

const EMPTY_CART = { cartId: null, userId: null, cartItems: [], totalAmount: 0 }

/* ------------------------------------------------------------------
 * Read
 * ------------------------------------------------------------------ */
export function useCart() {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  return useQuery({
    queryKey: CART_KEY,
    enabled: isAuthed,
    queryFn: async () => {
      const res = await api.get('/api/cart')
      return res.data?.data || EMPTY_CART
    },
    staleTime: 15_000,
  })
}

/* Cheap selector for header badge / nav indicator. Doesn't trigger
 * a fetch on its own (cart query is enabled where needed). */
export function useCartItemCount() {
  const { data } = useCart()
  return (data?.cartItems || []).reduce((n, it) => n + (it.quantity || 0), 0)
}

/* ------------------------------------------------------------------
 * Internal: shared optimistic-update helper.
 * `mutator(prevCart) -> nextCart`
 * ------------------------------------------------------------------ */
function makeOptimistic(qc, mutator) {
  return async () => {
    await qc.cancelQueries({ queryKey: CART_KEY })
    const previous = qc.getQueryData(CART_KEY) || EMPTY_CART
    qc.setQueryData(CART_KEY, mutator(previous))
    return { previous }
  }
}

function recomputeTotal(items) {
  return items.reduce((s, it) => s + (it.price || 0) * (it.quantity || 0), 0)
}

/* ------------------------------------------------------------------
 * Add to cart
 * ------------------------------------------------------------------ */
export function useAddToCart() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, variantId = null, quantity = 1, product }) => {
      const res = await api.post('/api/cart', { productId, variantId, quantity })
      return res.data?.data
    },
    onMutate: ({ productId, variantId = null, quantity = 1, product }) =>
      makeOptimistic(qc, (prev) => {
        const items = [...(prev.cartItems || [])]
        // A product+variant pair is a single cart line; product without
        // a variant is its own line. We match on both so adding the 500g
        // variant of an item already in the cart as 250g creates a new
        // line rather than incrementing the wrong one.
        const idx = items.findIndex(
          (it) => it.productId === productId && (it.variantId || null) === variantId,
        )
        // Variant-aware price/label snapshot from the product payload when
        // the caller passed it (optimistic UI only - server is authoritative).
        const variant = variantId && product?.variants
          ? product.variants.find((v) => v.variantId === variantId)
          : null
        const linePrice = variant ? variant.price : product?.price
        const variantLabel = variant ? variant.label : null
        if (idx >= 0) {
          const next = { ...items[idx], quantity: items[idx].quantity + quantity }
          next.subtotal = next.price * next.quantity
          items[idx] = next
        } else if (product) {
          items.push({
            cartItemId: `tmp-${productId}-${variantId || 'base'}`,
            productId,
            variantId,
            variantLabel,
            productName: product.name,
            imageUrl: product.primaryImageUrl || '',
            price: linePrice,
            quantity,
            subtotal: (linePrice || 0) * quantity,
          })
        }
        return { ...prev, cartItems: items, totalAmount: recomputeTotal(items) }
      })(),
    onError: (err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(CART_KEY, ctx.previous)
      toast.error(err?.response?.data?.message || 'Could not add to cart')
    },
    onSuccess: (cart) => {
      if (cart) qc.setQueryData(CART_KEY, cart)
      toast.success('Added to cart')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: CART_KEY }),
  })
}

/* ------------------------------------------------------------------
 * Update item quantity (PUT; quantity=0 -> backend removes)
 * ------------------------------------------------------------------ */
export function useUpdateCartItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, variantId = null, quantity }) => {
      const res = await api.put('/api/cart', { productId, variantId, quantity })
      return res.data?.data
    },
    onMutate: ({ productId, variantId = null, quantity }) =>
      makeOptimistic(qc, (prev) => {
        let items = prev.cartItems || []
        const matches = (it) =>
          it.productId === productId && (it.variantId || null) === variantId
        if (quantity <= 0) {
          items = items.filter((it) => !matches(it))
        } else {
          items = items.map((it) =>
            matches(it)
              ? { ...it, quantity, subtotal: it.price * quantity }
              : it,
          )
        }
        return { ...prev, cartItems: items, totalAmount: recomputeTotal(items) }
      })(),
    onError: (err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(CART_KEY, ctx.previous)
      toast.error(err?.response?.data?.message || 'Could not update cart')
    },
    onSuccess: (cart) => {
      if (cart) qc.setQueryData(CART_KEY, cart)
    },
    onSettled: () => qc.invalidateQueries({ queryKey: CART_KEY }),
  })
}

/* ------------------------------------------------------------------
 * Remove a single item
 * ------------------------------------------------------------------ */
export function useRemoveCartItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, variantId = null }) => {
      const qs = new URLSearchParams({ productId })
      if (variantId) qs.set('variantId', variantId)
      const res = await api.delete(`/api/cart?${qs.toString()}`)
      return res.data?.data
    },
    onMutate: ({ productId, variantId = null }) =>
      makeOptimistic(qc, (prev) => {
        const items = (prev.cartItems || []).filter(
          (it) => !(it.productId === productId && (it.variantId || null) === variantId),
        )
        return { ...prev, cartItems: items, totalAmount: recomputeTotal(items) }
      })(),
    onError: (err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(CART_KEY, ctx.previous)
      toast.error(err?.response?.data?.message || 'Could not remove item')
    },
    onSuccess: (cart) => {
      if (cart) qc.setQueryData(CART_KEY, cart)
      toast.success('Removed from cart')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: CART_KEY }),
  })
}

/* ------------------------------------------------------------------
 * Clear cart entirely
 * ------------------------------------------------------------------ */
export function useClearCart() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const res = await api.delete('/api/cart')
      return res.data?.data
    },
    onMutate: makeOptimistic(qc, (prev) => ({
      ...prev,
      cartItems: [],
      totalAmount: 0,
    })),
    onError: (err, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(CART_KEY, ctx.previous)
      toast.error(err?.response?.data?.message || 'Could not clear cart')
    },
    onSuccess: (cart) => {
      if (cart) qc.setQueryData(CART_KEY, cart)
      toast.success('Cart cleared')
    },
    onSettled: () => qc.invalidateQueries({ queryKey: CART_KEY }),
  })
}
