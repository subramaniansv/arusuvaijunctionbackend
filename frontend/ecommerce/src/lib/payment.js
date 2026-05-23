/**
 * Razorpay two-phase payment hooks.
 *
 * Backend contract (PaymentController):
 *   POST /api/payment?action=initiate
 *     body: { shippingAddress, phone, item? }    (same shape as /api/order)
 *     -> { orderId, razorpayOrderId, amount(paise), currency, keyId }
 *
 *   POST /api/payment?action=verify
 *     body: { orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature }
 *     -> order (with status=PAID)
 *
 * The two phases are stitched together in `useRazorpayCheckout` below,
 * which opens the Razorpay popup between phases. Callers only deal
 * with that single hook.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

import { api } from './api'

/* ------------------------------------------------------------------
 * Lazy-load the Razorpay Checkout SDK on demand. The <script> is no
 * longer in index.html so the home page doesn't pay for it. The first
 * caller injects the tag; subsequent callers reuse the same Promise.
 * ------------------------------------------------------------------ */
const RZP_SRC = 'https://checkout.razorpay.com/v1/checkout.js'
let razorpayPromise = null
function loadRazorpayScript() {
  if (typeof window === 'undefined') return Promise.reject(new Error('no window'))
  if (window.Razorpay) return Promise.resolve(window.Razorpay)
  if (razorpayPromise) return razorpayPromise
  razorpayPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${RZP_SRC}"]`)
    const onOk = () => resolve(window.Razorpay)
    const onErr = () => {
      razorpayPromise = null
      reject(new Error('Failed to load Razorpay SDK'))
    }
    if (existing) {
      existing.addEventListener('load', onOk, { once: true })
      existing.addEventListener('error', onErr, { once: true })
      return
    }
    const s = document.createElement('script')
    s.src = RZP_SRC
    s.async = true
    s.onload = onOk
    s.onerror = onErr
    document.head.appendChild(s)
  })
  return razorpayPromise
}

/* ------------------------------------------------------------------
 * Phase 1 - initiate (raw mutation, exported for completeness)
 * ------------------------------------------------------------------ */
export function usePaymentInitiate() {
  return useMutation({
    mutationFn: async ({ shippingAddress, phone, item }) => {
      const body = { shippingAddress, phone }
      if (item && item.productId) {
        body.item = {
          productId: item.productId,
          quantity: item.quantity > 0 ? item.quantity : 1,
        }
        if (item.variantId) body.item.variantId = item.variantId
      }
      const res = await api.post('/api/payment?action=initiate', body)
      return res.data?.data // { orderId, razorpayOrderId, amount, currency, keyId }
    },
  })
}

/* ------------------------------------------------------------------
 * Phase 2 - verify (raw mutation, exported for completeness)
 * ------------------------------------------------------------------ */
export function usePaymentVerify() {
  return useMutation({
    mutationFn: async (payload) => {
      // payload: { orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature }
      const res = await api.post('/api/payment?action=verify', payload)
      return res.data?.data // verified order
    },
  })
}

/* ------------------------------------------------------------------
 * Combined hook: opens Razorpay popup, awaits success/dismissal,
 * verifies signature server-side, returns the final order.
 *
 * Usage:
 *   const pay = useRazorpayCheckout()
 *   const order = await pay({ shippingAddress, phone, item? }, { name: 'Customer' })
 *   if (order) navigate(`/orders/${order.orderId}`)
 *
 * Returns null if the user dismissed the popup without paying.
 * Throws on initiate failure or signature verification failure.
 * ------------------------------------------------------------------ */
export function useRazorpayCheckout() {
  const initiate = usePaymentInitiate()
  const verify = usePaymentVerify()
  const qc = useQueryClient()

  return async function payWithRazorpay(checkoutPayload, prefill = {}) {
    if (typeof window === 'undefined') {
      throw new Error('Razorpay SDK requires a browser')
    }
    // Lazy-load the Razorpay Checkout SDK on first call. Keeps the
    // ~30 KB script off the initial page load.
    if (!window.Razorpay) {
      try {
        await loadRazorpayScript()
      } catch {
        toast.error('Payment SDK failed to load. Refresh and try again.')
        throw new Error('Razorpay SDK not loaded')
      }
    }

    // Phase 1: create internal order + Razorpay order. If this fails the
    // backend has already rolled back, so no zombie order is left around.
    let init
    try {
      init = await initiate.mutateAsync(checkoutPayload)
    } catch (err) {
      const msg = err?.response?.data?.message || 'Could not start payment'
      toast.error(msg)
      throw err
    }

    if (!init?.keyId) {
      toast.error('Payment gateway is not configured')
      throw new Error('missing keyId from initiate response')
    }

    const order = await new Promise((resolve, reject) => {
      const rzp = new window.Razorpay({
        key: init.keyId,
        order_id: init.razorpayOrderId,
        amount: init.amount,
        currency: init.currency || 'INR',
        name: 'Arusuvai',
        description: 'Order #' + String(init.orderId).slice(0, 8).toUpperCase(),
        prefill: {
          name: prefill.name || '',
          email: prefill.email || '',
          contact: prefill.contact || '',
        },
        theme: { color: '#7a3f2f' },

        // Razorpay calls this on a SUCCESSFUL payment. We must still verify
        // the signature server-side - never trust this callback alone.
        handler: async function (rp) {
          try {
            const verified = await verify.mutateAsync({
              orderId: init.orderId,
              razorpayOrderId: rp.razorpay_order_id,
              razorpayPaymentId: rp.razorpay_payment_id,
              razorpaySignature: rp.razorpay_signature,
            })
            // Server has flipped status to PAID, cleared cart (if CART
            // type), and decremented stock. Refresh affected caches.
            qc.invalidateQueries({ queryKey: ['orders'] })
            qc.invalidateQueries({ queryKey: ['order', init.orderId] })
            qc.invalidateQueries({ queryKey: ['cart'] })
            toast.success('Payment successful')
            resolve(verified)
          } catch (err) {
            const msg = err?.response?.data?.message || 'Payment verification failed'
            toast.error(msg)
            reject(err)
          }
        },
        modal: {
          // User closed the popup without paying. The internal order
          // stays in PAYMENT_PENDING - admin can clean it up or the
          // user can retry.
          ondismiss: function () {
            toast('Payment cancelled', { icon: '⚠️' })
            resolve(null)
          },
        },
      })

      // If Razorpay reports a payment failure (e.g. card declined) the
      // SDK fires this event - we don't auto-call verify because there
      // is no payment to verify. Webhook will mark PAYMENT_FAILED.
      rzp.on('payment.failed', function (resp) {
        const desc = resp?.error?.description || 'Payment failed'
        toast.error(desc)
        resolve(null)
      })

      rzp.open()
    })

    return order
  }
}
