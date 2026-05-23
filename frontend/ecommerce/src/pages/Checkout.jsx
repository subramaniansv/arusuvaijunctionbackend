/**
 * Checkout page (protected).
 *
 * Collects a structured shipping address (line1, line2?, city, state,
 * pincode, country) + phone, assembles them into the single
 * `shippingAddress` string the backend expects, initiates a Razorpay
 * payment via `useRazorpayCheckout`, and on signature-verified success
 * navigates to the order detail page.
 *
 * India PIN autofill uses the free public api.postalpincode.in
 * service (no key, CORS-enabled). When the user types a 6-digit PIN,
 * we fetch the matching post office and pre-fill state + city.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowLeft, MapPin, Phone, ShieldCheck, Loader2, Check } from 'lucide-react'

import {
  Container,
  Card,
  Input,
  Select,
  Button,
  PriceTag,
  Divider,
} from '../components'
import { useCart } from '../lib/cart'
import { useRazorpayCheckout } from '../lib/payment'
import { useMyProfile } from '../lib/me'
import './Checkout.css'

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e"

/* ------------------------------------------------------------------
 * Country list. Keep India on top because that's the primary market.
 * (Add more as needed - the rest fall back to free-text city/state
 * with no pincode lookup.)
 * ------------------------------------------------------------------ */
const COUNTRIES = [
  { value: 'IN', label: 'India' },
  { value: 'US', label: 'United States' },
  { value: 'GB', label: 'United Kingdom' },
  { value: 'AE', label: 'United Arab Emirates' },
  { value: 'SG', label: 'Singapore' },
  { value: 'AU', label: 'Australia' },
  { value: 'CA', label: 'Canada' },
  { value: 'MY', label: 'Malaysia' },
  { value: 'OTHER', label: 'Other' },
]

const INDIAN_STATES = [
  'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh',
  'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka',
  'Kerala', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Punjab', 'Rajasthan', 'Sikkim',
  'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand',
  'West Bengal',
  // Union Territories
  'Andaman and Nicobar Islands', 'Chandigarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Jammu and Kashmir',
  'Ladakh', 'Lakshadweep', 'Puducherry',
]

const PIN_PATTERNS = {
  IN: { regex: /^\d{6}$/, label: '6-digit PIN code' },
  US: { regex: /^\d{5}(-\d{4})?$/, label: '5-digit ZIP' },
  GB: { regex: /^[A-Z0-9 ]{5,8}$/i, label: 'UK postcode' },
  AE: { regex: /^.{3,10}$/, label: 'Postcode' },
  SG: { regex: /^\d{6}$/, label: '6-digit postal code' },
  AU: { regex: /^\d{4}$/, label: '4-digit postcode' },
  CA: { regex: /^[A-Z]\d[A-Z] ?\d[A-Z]\d$/i, label: 'Canadian postal code' },
  MY: { regex: /^\d{5}$/, label: '5-digit postcode' },
  OTHER: { regex: /^.{2,12}$/, label: 'Postal code' },
}

const schema = z.object({
  fullName: z.string().trim().min(2, 'Please enter the recipient name').max(80),
  line1: z.string().trim().min(3, 'Please enter the door / street').max(120),
  line2: z.string().trim().max(120).optional().or(z.literal('')),
  city: z.string().trim().min(2, 'Please enter the city').max(60),
  state: z.string().trim().min(2, 'Please enter the state').max(60),
  pincode: z.string().trim().min(3, 'Please enter the postal code').max(12),
  country: z.string().min(2, 'Please select a country'),
  phone: z
    .string()
    .trim()
    .regex(/^[+0-9 \-()]{7,20}$/, 'Please enter a valid phone number'),
})

/* ------------------------------------------------------------------
 * Compose the structured fields into the single shippingAddress
 * string the backend stores.
 * ------------------------------------------------------------------ */
function buildShippingAddress(v) {
  const parts = [
    v.fullName,
    v.line1,
    v.line2,
    [v.city, v.state, v.pincode].filter(Boolean).join(', '),
    COUNTRIES.find((c) => c.value === v.country)?.label || v.country,
  ]
  return parts.filter((x) => x && String(x).trim()).join('\n')
}

export default function Checkout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { data: cart, isLoading } = useCart()
  const payWithRazorpay = useRazorpayCheckout()
  const { data: profile } = useMyProfile()
  const [paying, setPaying] = useState(false)

  /* -----------------------------------------------------------------
   * Buy-Now mode: when arriving with router state.buyNow, this checkout
   * is for ONE product only (productId/variantId/quantity + a small
   * display snapshot). We skip the cart entirely - both for the
   * summary on this page and for the server call.
   * ----------------------------------------------------------------- */
  const buyNow = location.state?.buyNow || null

  const items = useMemo(() => {
    if (buyNow) {
      const qty = buyNow.quantity > 0 ? buyNow.quantity : 1
      return [{
        cartItemId: 'buy-now',
        productId: buyNow.productId,
        productName: buyNow.snapshot?.name || 'Selected product',
        imageUrl: buyNow.snapshot?.imageUrl || null,
        price: Number(buyNow.snapshot?.price) || 0,
        quantity: qty,
        subtotal: (Number(buyNow.snapshot?.price) || 0) * qty,
      }]
    }
    return cart?.cartItems || []
  }, [buyNow, cart])

  const subtotal = buyNow
    ? items[0].subtotal
    : (cart?.totalAmount ?? items.reduce(
        (s, it) => s + (it.subtotal ?? it.price * it.quantity),
        0,
      ))

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: '',
      line1: '',
      line2: '',
      city: '',
      state: '',
      pincode: '',
      country: 'IN',
      phone: '',
    },
  })

  const country = watch('country')
  const pincode = watch('pincode')

  // PIN lookup state (India only)
  const [pinStatus, setPinStatus] = useState('idle') // idle | loading | found | invalid | error
  const lastLookupRef = useRef('')

  useEffect(() => {
    if (country !== 'IN') {
      setPinStatus('idle')
      return
    }
    const pin = (pincode || '').trim()
    if (!/^\d{6}$/.test(pin)) {
      setPinStatus('idle')
      return
    }
    if (lastLookupRef.current === pin) return
    lastLookupRef.current = pin

    let cancelled = false
    const t = setTimeout(async () => {
      try {
        setPinStatus('loading')
        const res = await fetch(`https://api.postalpincode.in/pincode/${pin}`)
        const json = await res.json()
        if (cancelled) return
        const entry = Array.isArray(json) ? json[0] : null
        if (entry?.Status === 'Success' && entry.PostOffice?.length) {
          const po = entry.PostOffice[0]
          setValue('state', po.State || '', { shouldValidate: true })
          // Prefer District; fall back to Block / Name if District is empty.
          setValue('city', po.District || po.Block || po.Name || '', {
            shouldValidate: true,
          })
          setPinStatus('found')
        } else {
          setPinStatus('invalid')
        }
      } catch {
        if (!cancelled) setPinStatus('error')
      }
    }, 300) // tiny debounce

    return () => {
      cancelled = true
      clearTimeout(t)
    }
  }, [country, pincode, setValue])

  // Bounce out of checkout if the cart is empty after loading.
  // (Skip this check in Buy-Now mode - the cart is irrelevant there.)
  useEffect(() => {
    if (buyNow) return
    if (!isLoading && items.length === 0) {
      navigate('/cart', { replace: true })
    }
  }, [buyNow, isLoading, items.length, navigate])

  const onSubmit = async (values) => {
    const payload = {
      shippingAddress: buildShippingAddress(values),
      phone: values.phone,
    }
    if (buyNow) {
      payload.item = {
        productId: buyNow.productId,
        variantId: buyNow.variantId || null,
        quantity: buyNow.quantity > 0 ? buyNow.quantity : 1,
      }
    }

    try {
      setPaying(true)
      const order = await payWithRazorpay(payload, {
        name: values.fullName,
        email: profile?.email || '',
        contact: values.phone,
      })
      if (order?.orderId) {
        navigate(`/orders/${order.orderId}`, {
          replace: true,
          state: { justPaid: true, order },
        })
      }
      // order === null => popup dismissed; stay on checkout.
    } catch {
      // toast handled inside the hook
    } finally {
      setPaying(false)
    }
  }

  if (isLoading && !buyNow) {
    return (
      <Container size="lg" className="checkout">
        <p className="text-muted">Loading checkout…</p>
      </Container>
    )
  }
  if (items.length === 0) return null // useEffect will redirect (cart mode only)

  const pinHint =
    PIN_PATTERNS[country]?.label || 'Postal code'

  /* ----- PIN status decoration (India only) ----- */
  let pinStatusNode = null
  if (country === 'IN') {
    if (pinStatus === 'loading') {
      pinStatusNode = (
        <span className="checkout__pin-status">
          <Loader2 size={14} className="checkout__spin" aria-hidden="true" /> Looking up…
        </span>
      )
    } else if (pinStatus === 'found') {
      pinStatusNode = (
        <span className="checkout__pin-status checkout__pin-status--ok">
          <Check size={14} aria-hidden="true" /> Found — state and city auto-filled
        </span>
      )
    } else if (pinStatus === 'invalid') {
      pinStatusNode = (
        <span className="checkout__pin-status checkout__pin-status--warn">
          PIN not recognised — please enter state and city manually.
        </span>
      )
    } else if (pinStatus === 'error') {
      pinStatusNode = (
        <span className="checkout__pin-status checkout__pin-status--warn">
          Couldn&apos;t reach PIN lookup service. Enter state and city manually.
        </span>
      )
    }
  }

  return (
    <Container size="xl" className="checkout">
      <header className="checkout__header">
        <h1 className="checkout__title">
          {buyNow ? 'Buy now' : 'Checkout'}
        </h1>
        <p className="checkout__sub">
          {buyNow
            ? 'Single-item express checkout — your cart is not affected.'
            : 'Review your order and add shipping details.'}
        </p>
      </header>

      <form className="checkout__layout" onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* ---- left: form ---- */}
        <section className="checkout__form" aria-label="Shipping details">
          <Card padding="lg" className="checkout__card">
            <h2 className="checkout__section-title">
              <MapPin size={18} aria-hidden="true" /> Shipping address
            </h2>

            <Input
              label="Full name"
              autoComplete="name"
              placeholder="Recipient name"
              error={errors.fullName?.message}
              {...register('fullName')}
            />

            <Input
              label="Address line 1"
              autoComplete="address-line1"
              placeholder="Door no, street, area"
              error={errors.line1?.message}
              {...register('line1')}
            />

            <Input
              label="Address line 2 (optional)"
              autoComplete="address-line2"
              placeholder="Landmark, apartment, etc."
              error={errors.line2?.message}
              {...register('line2')}
            />

            <div className="checkout__grid-3">
              <Select
                label="Country"
                options={COUNTRIES}
                error={errors.country?.message}
                {...register('country')}
              />
              <Input
                label={country === 'IN' ? 'PIN code' : 'Postal / ZIP code'}
                inputMode={country === 'IN' || country === 'US' ? 'numeric' : 'text'}
                autoComplete="postal-code"
                placeholder={pinHint}
                error={errors.pincode?.message}
                hint={!errors.pincode ? pinHint : undefined}
                {...register('pincode')}
              />
              <div className="checkout__pin-slot">
                {pinStatusNode}
              </div>
            </div>

            <div className="checkout__grid-2">
              {country === 'IN' ? (
                <Select
                  label="State"
                  placeholder="Select state"
                  options={INDIAN_STATES.map((s) => ({ value: s, label: s }))}
                  error={errors.state?.message}
                  {...register('state')}
                />
              ) : (
                <Input
                  label="State / Region"
                  autoComplete="address-level1"
                  error={errors.state?.message}
                  {...register('state')}
                />
              )}
              <Input
                label="City"
                autoComplete="address-level2"
                error={errors.city?.message}
                {...register('city')}
              />
            </div>
          </Card>

          <Card padding="lg" className="checkout__card">
            <h2 className="checkout__section-title">
              <Phone size={18} aria-hidden="true" /> Contact
            </h2>
            <Input
              label="Phone number"
              type="tel"
              inputMode="tel"
              placeholder="+91 9XXXXXXXXX"
              autoComplete="tel"
              error={errors.phone?.message}
              {...register('phone')}
            />
            <p className="checkout__hint">
              We&apos;ll use this to confirm your order if we need to.
            </p>
          </Card>

          <div className="checkout__actions">
            {buyNow ? (
              <Button
                type="button"
                variant="ghost"
                leftIcon={<ArrowLeft size={16} />}
                onClick={() => navigate(-1)}
              >
                Back
              </Button>
            ) : (
              <Button
                as={Link}
                to="/cart"
                variant="ghost"
                leftIcon={<ArrowLeft size={16} />}
              >
                Back to cart
              </Button>
            )}
          </div>
        </section>

        {/* ---- right: summary ---- */}
        <aside className="checkout__summary" aria-label="Order summary">
          <Card padding="lg" className="checkout__summary-card">
            <h2 className="checkout__section-title">Order summary</h2>

            <ul className="checkout__lines">
              {items.map((it) => (
                <li className="checkout__line" key={it.cartItemId || it.productId}>
                  <span className="checkout__line-thumb">
                    <img
                      src={it.imageUrl || PLACEHOLDER}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      onError={(e) => { e.currentTarget.src = PLACEHOLDER }}
                    />
                    <span className="checkout__line-qty" aria-hidden="true">{it.quantity}</span>
                  </span>
                  <span className="checkout__line-body">
                    <span className="checkout__line-name" title={it.productName}>
                      {it.productName}
                    </span>
                    <span className="checkout__line-meta">
                      Qty {it.quantity} · ₹{Number(it.price).toLocaleString('en-IN')}
                    </span>
                  </span>
                  <PriceTag
                    amount={it.subtotal ?? it.price * it.quantity}
                    size="sm"
                  />
                </li>
              ))}
            </ul>

            <Divider />
            <div className="checkout__sum-line">
              <span>Subtotal</span>
              <PriceTag amount={subtotal} size="md" />
            </div>
            <div className="checkout__sum-line">
              <span>Shipping</span>
              <span className="checkout__free">Free</span>
            </div>
            <Divider />
            <div className="checkout__sum-line checkout__sum-line--strong">
              <span>Total</span>
              <PriceTag amount={subtotal} size="lg" />
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              fullWidth
              loading={isSubmitting || paying}
              className="checkout__place"
            >
              {`Pay ₹${Number(subtotal).toLocaleString('en-IN')}`}
            </Button>

            <p className="checkout__secure">
              <ShieldCheck size={14} aria-hidden="true" />
              Secure payment via Razorpay. Cards / UPI / netbanking.
            </p>
          </Card>
        </aside>
      </form>
    </Container>
  )
}
