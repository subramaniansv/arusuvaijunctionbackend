/* ------------------------------------------------------------------
 * Orders data hooks (React Query).
 *
 * Backend contract (OrderController):
 *   POST   /api/order         body { shippingAddress, phone }   -> cart-based checkout
 *   GET    /api/order                                            -> list (paginated)
 *   GET    /api/order?orderID=<uuid>                             -> single order
 *
 *   NB: backend param name is `orderID` (capital ID), not `orderId`.
 *
 * Order shape:
 *   { orderId, userId, status, totalAmount,
 *     shippingAddress, phone, orderedAt, updatedAt,
 *     orderItems: [{ orderItemId, productId, productName,
 *                    imageUrl, price, quantity }],
 *     whatsappMessage, whatsappLink }    // transient, only on checkout response
 *
 * Status enum: PENDING | CONFIRMED | SHIPPED | DELIVERED | CANCELLED.
 *
 * Checkout invalidates the cart cache so the nav badge clears and the
 * Cart page renders its empty state.
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

import { api } from './api'
import { useAuthStore } from '../stores/authStore'

const ORDERS_KEY = ['orders']
const ORDER_KEY = (id) => ['order', id]

/* ------------------------------------------------------------------
 * List
 * ------------------------------------------------------------------ */
export function useOrders({ limit = 20, offset = 0 } = {}) {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  return useQuery({
    queryKey: [...ORDERS_KEY, { limit, offset }],
    enabled: isAuthed,
    queryFn: async () => {
      const res = await api.get(`/api/order?limit=${limit}&offset=${offset}`)
      return res.data?.data || []
    },
    staleTime: 30_000,
  })
}

/* ------------------------------------------------------------------
 * Single order
 * ------------------------------------------------------------------ */
export function useOrder(orderId) {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  return useQuery({
    queryKey: ORDER_KEY(orderId),
    enabled: isAuthed && !!orderId,
    queryFn: async () => {
      const res = await api.get(
        `/api/order?orderID=${encodeURIComponent(orderId)}`,
      )
      return res.data?.data
    },
    staleTime: 30_000,
  })
}

/* ------------------------------------------------------------------
 * Checkout (POST). Body: { shippingAddress, phone }.
 *
 * On success we:
 *   - seed the single-order cache so OrderDetail shows the freshly
 *     placed order (including whatsappLink/whatsappMessage) without a
 *     refetch,
 *   - invalidate the orders list,
 *   - invalidate the cart (the backend clears it server-side).
 * ------------------------------------------------------------------ */
export function useCheckout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ shippingAddress, phone, item }) => {
      // If `item` is supplied this is a Buy-Now (single-product) order
      // that bypasses the cart server-side. Otherwise it's a normal
      // cart-based checkout.
      const body = { shippingAddress, phone }
      if (item && item.productId) {
        body.item = {
          productId: item.productId,
          quantity: item.quantity > 0 ? item.quantity : 1,
        }
        if (item.variantId) body.item.variantId = item.variantId
      }
      const res = await api.post('/api/order', body)
      return { order: res.data?.data, buyNow: !!body.item }
    },
    onSuccess: ({ order, buyNow }) => {
      if (order?.orderId) qc.setQueryData(ORDER_KEY(order.orderId), order)
      qc.invalidateQueries({ queryKey: ORDERS_KEY })
      // Cart-based checkout clears the cart server-side; Buy-Now leaves
      // the cart untouched so we don't need to invalidate it.
      if (!buyNow) qc.invalidateQueries({ queryKey: ['cart'] })
      toast.success('Order placed')
    },
    onError: (err) => {
      toast.error(err?.response?.data?.message || 'Checkout failed')
    },
  })
}

/* ------------------------------------------------------------------
 * Helpers (status badge + date format).
 * ------------------------------------------------------------------ */
export const ORDER_STATUS_VARIANT = {
  PENDING:   'warning',
  CONFIRMED: 'info',
  SHIPPED:   'primary',
  DELIVERED: 'success',
  CANCELLED: 'danger',
}

export function formatOrderDate(iso) {
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

export function shortOrderId(uuid) {
  if (!uuid) return ''
  return `#${String(uuid).slice(0, 8).toUpperCase()}`
}
