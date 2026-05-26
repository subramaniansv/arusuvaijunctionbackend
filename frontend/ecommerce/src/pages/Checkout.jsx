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
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowLeft,
  MapPin,
  ShieldCheck,
  Loader2,
  Check,
  BookUser,
} from "lucide-react";

import {
  Container,
  Card,
  Input,
  Select,
  Button,
  PriceTag,
  Divider,
} from "../components";
import { useCart } from "../lib/cart";
import { useRazorpayCheckout } from "../lib/payment";
import { useMyProfile } from "../lib/me";
import { useAddresses } from "../lib/addresses";
import { calcShipping, getZoneLabel, FREE_ABOVE_INR } from "../lib/shipping";
import "./Checkout.css";

const PLACEHOLDER =
  "data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 4 3'%3e%3crect width='4' height='3' fill='%23f5f5f0'/%3e%3c/svg%3e";

const INDIAN_STATES = [
  "Andhra Pradesh",
  "Arunachal Pradesh",
  "Assam",
  "Bihar",
  "Chhattisgarh",
  "Goa",
  "Gujarat",
  "Haryana",
  "Himachal Pradesh",
  "Jharkhand",
  "Karnataka",
  "Kerala",
  "Madhya Pradesh",
  "Maharashtra",
  "Manipur",
  "Meghalaya",
  "Mizoram",
  "Nagaland",
  "Odisha",
  "Punjab",
  "Rajasthan",
  "Sikkim",
  "Tamil Nadu",
  "Telangana",
  "Tripura",
  "Uttar Pradesh",
  "Uttarakhand",
  "West Bengal",
  // Union Territories
  "Andaman and Nicobar Islands",
  "Chandigarh",
  "Dadra and Nagar Haveli and Daman and Diu",
  "Delhi",
  "Jammu and Kashmir",
  "Ladakh",
  "Lakshadweep",
  "Puducherry",
];

const schema = z.object({
  fullName: z.string().trim().min(2, "Please enter the recipient name").max(80),
  line1: z.string().trim().min(3, "Please enter the door / street").max(120),
  line2: z.string().trim().max(120).optional().or(z.literal("")),
  city: z.string().trim().min(2, "Please enter the city").max(60),
  state: z.string().trim().min(2, "Please enter the state").max(60),
  pincode: z
    .string()
    .trim()
    .regex(/^\d{6}$/, "Please enter a valid 6-digit PIN"),
  phone: z
    .string()
    .trim()
    .regex(/^[+0-9 \-()]{7,20}$/, "Please enter a valid phone number"),
});

/* ------------------------------------------------------------------
 * Compose the structured fields into the single shippingAddress
 * string the backend stores.
 * ------------------------------------------------------------------ */
function buildShippingAddress(v) {
  const parts = [
    v.fullName,
    v.line1,
    v.line2,
    [v.city, v.state, v.pincode].filter(Boolean).join(", "),
    "India",
  ];
  return parts.filter((x) => x && String(x).trim()).join("\n");
}

export default function Checkout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { data: cart, isLoading } = useCart();
  const payWithRazorpay = useRazorpayCheckout();
  const { data: profile } = useMyProfile();
  const { data: savedAddresses = [] } = useAddresses();
  const [paying, setPaying] = useState(false);
  const [selectedSavedId, setSelectedSavedId] = useState("");

  /* -----------------------------------------------------------------
   * Buy-Now mode: when arriving with router state.buyNow, this checkout
   * is for ONE product only (productId/variantId/quantity + a small
   * display snapshot). We skip the cart entirely - both for the
   * summary on this page and for the server call.
   * ----------------------------------------------------------------- */
  const buyNow = location.state?.buyNow || null;

  const items = useMemo(() => {
    if (buyNow) {
      const qty = buyNow.quantity > 0 ? buyNow.quantity : 1;
      return [
        {
          cartItemId: "buy-now",
          productId: buyNow.productId,
          productName: buyNow.snapshot?.name || "Selected product",
          imageUrl: buyNow.snapshot?.imageUrl || null,
          price: Number(buyNow.snapshot?.price) || 0,
          quantity: qty,
          subtotal: (Number(buyNow.snapshot?.price) || 0) * qty,
        },
      ];
    }
    return cart?.cartItems || [];
  }, [buyNow, cart]);

  const subtotal = buyNow
    ? items[0].subtotal
    : (cart?.totalAmount ??
      items.reduce((s, it) => s + (it.subtotal ?? it.price * it.quantity), 0));

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: "",
      line1: "",
      line2: "",
      city: "",
      state: "",
      pincode: "",
      phone: "",
    },
  });

  const pincode = watch("pincode");

  // When the user selects a saved address, fill the form fields.
  useEffect(() => {
    if (!selectedSavedId) return;
    const addr = savedAddresses.find((a) => a.addressId === selectedSavedId);
    if (!addr) return;
    setValue("fullName", addr.fullName || "", { shouldValidate: false });
    setValue("phone", addr.phone || "", { shouldValidate: false });
    setValue("line1", addr.line1 || "", { shouldValidate: false });
    setValue("line2", addr.line2 || "", { shouldValidate: false });
    setValue("city", addr.city || "", { shouldValidate: false });
    setValue("state", addr.state || "", { shouldValidate: false });
    setValue("pincode", addr.pincode || "", { shouldValidate: false });
  }, [selectedSavedId, savedAddresses, setValue]);

  const totalQty = items.reduce((s, it) => s + (it.quantity || 1), 0);
  const shippingFee = calcShipping(pincode, totalQty, subtotal);
  const shippingZoneLabel = getZoneLabel(pincode);
  const total = subtotal + shippingFee;

  const [pinStatus, setPinStatus] = useState("idle"); // idle | loading | found | invalid | error
  const lastLookupRef = useRef("");

  useEffect(() => {
    const pin = (pincode || "").trim();
    if (!/^\d{6}$/.test(pin)) {
      setPinStatus("idle");
      return;
    }
    if (lastLookupRef.current === pin) return;
    lastLookupRef.current = pin;

    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        setPinStatus("loading");
        const res = await fetch(`https://api.postalpincode.in/pincode/${pin}`);
        const json = await res.json();
        if (cancelled) return;
        const entry = Array.isArray(json) ? json[0] : null;
        if (entry?.Status === "Success" && entry.PostOffice?.length) {
          const po = entry.PostOffice[0];
          setValue("state", po.State || "", { shouldValidate: true });
          // Prefer District; fall back to Block / Name if District is empty.
          setValue("city", po.District || po.Block || po.Name || "", {
            shouldValidate: true,
          });
          setPinStatus("found");
        } else {
          setPinStatus("invalid");
        }
      } catch {
        if (!cancelled) setPinStatus("error");
      }
    }, 300); // tiny debounce

    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [pincode, setValue]);

  // Bounce out of checkout if the cart is empty after loading.
  // (Skip this check in Buy-Now mode - the cart is irrelevant there.)
  useEffect(() => {
    if (buyNow) return;
    if (!isLoading && items.length === 0) {
      navigate("/cart", { replace: true });
    }
  }, [buyNow, isLoading, items.length, navigate]);

  const onSubmit = async (values) => {
    const payload = {
      shippingAddress: buildShippingAddress(values),
      phone: values.phone,
      shippingFee,
    };
    if (buyNow) {
      payload.item = {
        productId: buyNow.productId,
        variantId: buyNow.variantId || null,
        quantity: buyNow.quantity > 0 ? buyNow.quantity : 1,
      };
    }

    try {
      setPaying(true);
      const order = await payWithRazorpay(payload, {
        name: values.fullName,
        email: profile?.email || "",
        contact: values.phone,
      });
      if (order?.orderId) {
        navigate(`/orders/${order.orderId}`, {
          replace: true,
          state: { justPlaced: true, order },
        });
      }
      // order === null => popup dismissed; stay on checkout.
    } catch {
      // toast handled inside the hook
    } finally {
      setPaying(false);
    }
  };

  if (isLoading && !buyNow) {
    return (
      <Container size="lg" className="checkout">
        <p className="text-muted">Loading checkout…</p>
      </Container>
    );
  }
  if (items.length === 0) return null; // useEffect will redirect (cart mode only)

  const pinHint = "6-digit PIN code";

  /* ----- PIN status decoration -----
   * Only the optimistic states get UI. If the lookup fails or the PIN
   * is unrecognised we stay silent and let the user type city/state
   * manually - the goal is autofill convenience, not validation. */
  let pinStatusNode = null;
  if (pinStatus === "loading") {
    pinStatusNode = (
      <span className="checkout__pin-status">
        <Loader2 size={14} className="checkout__spin" aria-hidden="true" />{" "}
        Looking up…
      </span>
    );
  } else if (pinStatus === "found") {
    pinStatusNode = (
      <span className="checkout__pin-status checkout__pin-status--ok">
        <Check size={14} aria-hidden="true" /> Found - state and city
        auto-filled
      </span>
    );
  }

  return (
    <Container size="xl" className="checkout">
      <header className="checkout__header">
        <h1 className="checkout__title">{buyNow ? "Buy now" : "Checkout"}</h1>
        <p className="checkout__sub">
          {buyNow
            ? "Single-item express checkout - your cart is not affected."
            : "Review your order and add shipping details."}
        </p>
      </header>

      <form
        className="checkout__layout"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
      >
        {/* ---- left: form ---- */}
        <section className="checkout__form" aria-label="Shipping details">
          <Card padding="lg" className="checkout__card">
            <h2 className="checkout__section-title">
              <MapPin size={18} aria-hidden="true" /> Shipping address
            </h2>

            {/* ---- Saved-address picker (only shown when user has saved addresses) ---- */}
            {savedAddresses.length > 0 && (
              <div className="checkout__saved-addr">
                <label
                  htmlFor="savedAddrSelect"
                  className="checkout__saved-addr__label"
                >
                  <BookUser size={15} aria-hidden="true" /> Use a saved address
                </label>
                <select
                  id="savedAddrSelect"
                  className="checkout__saved-addr__select"
                  value={selectedSavedId}
                  onChange={(e) => setSelectedSavedId(e.target.value)}
                >
                  <option value="">— fill form manually —</option>
                  {savedAddresses.map((a) => (
                    <option key={a.addressId} value={a.addressId}>
                      {a.label ? `${a.label} – ` : ""}
                      {a.fullName}, {a.city}
                      {a.default ? " ★" : ""}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <Input
              label="Full name"
              autoComplete="name"
              placeholder="Recipient name"
              error={errors.fullName?.message}
              {...register("fullName")}
            />

            <Input
              label="Phone number"
              type="tel"
              inputMode="tel"
              placeholder="+91 9XXXXXXXXX"
              autoComplete="tel"
              hint="We'll use this to confirm your order if we need to."
              error={errors.phone?.message}
              {...register("phone")}
            />

            <Input
              label="Address line 1"
              autoComplete="address-line1"
              placeholder="Door no, street, area"
              error={errors.line1?.message}
              {...register("line1")}
            />

            <Input
              label="Address line 2 (optional)"
              autoComplete="address-line2"
              placeholder="Landmark, apartment, etc."
              error={errors.line2?.message}
              {...register("line2")}
            />
            <div className="checkout__grid-2">
              <Input
                label="City"
                autoComplete="address-level2"
                error={errors.city?.message}
                {...register("city")}
              />
              <Select
                label="State"
                placeholder="Select state"
                options={INDIAN_STATES.map((s) => ({ value: s, label: s }))}
                error={errors.state?.message}
                {...register("state")}
              />
            </div>
            <div className="checkout__grid-2">
              <Input
                label="PIN code"
                inputMode="numeric"
                autoComplete="postal-code"
                placeholder={pinHint}
                error={errors.pincode?.message}
                hint={!errors.pincode ? pinHint : undefined}
                {...register("pincode")}
              />
              <div className="checkout__pin-slot">{pinStatusNode}</div>
            </div>
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
                <li
                  className="checkout__line"
                  key={it.cartItemId || it.productId}
                >
                  <span className="checkout__line-thumb">
                    <img
                      src={it.imageUrl || PLACEHOLDER}
                      alt=""
                      loading="lazy"
                      decoding="async"
                      onError={(e) => {
                        e.currentTarget.src = PLACEHOLDER;
                      }}
                    />
                    <span className="checkout__line-qty" aria-hidden="true">
                      {it.quantity}
                    </span>
                  </span>
                  <span className="checkout__line-body">
                    <span
                      className="checkout__line-name"
                      title={it.productName}
                    >
                      {it.productName}
                    </span>
                    <span className="checkout__line-meta">
                      Qty {it.quantity} · ₹
                      {Number(it.price).toLocaleString("en-IN")}
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
              <span>
                Shipping
                {shippingZoneLabel && (
                  <span className="checkout__shipping-zone">
                    {" "}
                    – {shippingZoneLabel}
                  </span>
                )}
              </span>
              {shippingFee === 0 ? (
                <span className="checkout__free">
                  {subtotal >= FREE_ABOVE_INR ? "Free" : "TBD"}
                </span>
              ) : (
                <PriceTag amount={shippingFee} size="md" />
              )}
            </div>
            <Divider />
            <div className="checkout__sum-line checkout__sum-line--strong">
              <span>Total</span>
              <PriceTag amount={total} size="lg" />
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              fullWidth
              loading={isSubmitting || paying}
              className="checkout__place"
            >
              {`Pay ₹${Number(total).toLocaleString("en-IN")}`}
            </Button>

            <p className="checkout__secure">
              <ShieldCheck size={14} aria-hidden="true" />
              Secure payment via Razorpay. Cards / UPI / netbanking.
            </p>
          </Card>
        </aside>
      </form>
    </Container>
  );
}
