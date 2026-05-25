/**
 * Account page.
 *
 * Cards:
 *   1. Profile     - read-only view of the authenticated caller's profile.
 *   2. Saved addresses - CRUD address book; addresses can be used at checkout.
 *   3. Password    - change password form.
 */
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  User as UserIcon, Mail, ShieldCheck,
  Eye, EyeOff, KeyRound, Loader2, MailCheck, MailWarning,
  MapPin, Plus, Pencil, Trash2, Star,
} from 'lucide-react'

import { useMyProfile, useChangePassword, useResendVerification } from '../lib/me'
import {
  useAddresses, useCreateAddress, useUpdateAddress, useDeleteAddress,
} from '../lib/addresses'
import { useAuthStore } from '../stores/authStore'
import './Account.css'

/* ------------------------------------------------------------------ */
/* Address form schema                                                   */
/* ------------------------------------------------------------------ */
const addrSchema = z.object({
  label:    z.string().trim().max(80).optional().or(z.literal('')),
  fullName: z.string().trim().min(2, 'Name is required').max(120),
  phone:    z.string().trim().regex(/^[+0-9 \-()]{7,20}$/, 'Valid phone required'),
  line1:    z.string().trim().min(3, 'Address line 1 is required').max(160),
  line2:    z.string().trim().max(160).optional().or(z.literal('')),
  city:     z.string().trim().min(2, 'City is required').max(80),
  state:    z.string().trim().min(2, 'State is required').max(80),
  pincode:  z.string().trim().min(3, 'Pincode is required').max(20),
  country:  z.string().min(2, 'Country is required'),
  isDefault: z.boolean().optional(),
})

const ADDR_DEFAULTS = {
  label: '', fullName: '', phone: '', line1: '', line2: '',
  city: '', state: '', pincode: '', country: 'IN', isDefault: false,
}

/* ------------------------------------------------------------------ */
/* AddressForm – inline form (create or edit)                           */
/* ------------------------------------------------------------------ */
function AddressForm({ initial, onSave, onCancel, saving }) {
  const {
    register, handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(addrSchema),
    defaultValues: initial || ADDR_DEFAULTS,
  })

  return (
    <form className="addr-form" onSubmit={handleSubmit(onSave)} noValidate>
      <div className="addr-form__row addr-form__row--2">
        <div className="form-field">
          <label htmlFor="af-label">Label (optional)</label>
          <input id="af-label" placeholder="Home / Office…" {...register('label')} />
        </div>
        <div className="form-field">
          <label htmlFor="af-fullName">Full name *</label>
          <input id="af-fullName" {...register('fullName')} aria-invalid={!!errors.fullName || undefined} />
          {errors.fullName && <p className="form-error">{errors.fullName.message}</p>}
        </div>
      </div>

      <div className="form-field">
        <label htmlFor="af-phone">Phone *</label>
        <input id="af-phone" type="tel" inputMode="tel" {...register('phone')} aria-invalid={!!errors.phone || undefined} />
        {errors.phone && <p className="form-error">{errors.phone.message}</p>}
      </div>

      <div className="form-field">
        <label htmlFor="af-line1">Address line 1 *</label>
        <input id="af-line1" placeholder="Door no, street, area" {...register('line1')} aria-invalid={!!errors.line1 || undefined} />
        {errors.line1 && <p className="form-error">{errors.line1.message}</p>}
      </div>

      <div className="form-field">
        <label htmlFor="af-line2">Address line 2</label>
        <input id="af-line2" placeholder="Landmark, apartment (optional)" {...register('line2')} />
      </div>

      <div className="addr-form__row addr-form__row--3">
        <div className="form-field">
          <label htmlFor="af-city">City *</label>
          <input id="af-city" {...register('city')} aria-invalid={!!errors.city || undefined} />
          {errors.city && <p className="form-error">{errors.city.message}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="af-state">State *</label>
          <input id="af-state" {...register('state')} aria-invalid={!!errors.state || undefined} />
          {errors.state && <p className="form-error">{errors.state.message}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="af-pincode">Pincode *</label>
          <input id="af-pincode" inputMode="numeric" {...register('pincode')} aria-invalid={!!errors.pincode || undefined} />
          {errors.pincode && <p className="form-error">{errors.pincode.message}</p>}
        </div>
      </div>

      <div className="form-field">
        <label htmlFor="af-country">Country *</label>
        <input id="af-country" {...register('country')} aria-invalid={!!errors.country || undefined} />
        {errors.country && <p className="form-error">{errors.country.message}</p>}
      </div>

      <div className="form-field addr-form__checkbox">
        <label>
          <input type="checkbox" {...register('isDefault')} />
          Set as default address
        </label>
      </div>

      <div className="addr-form__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving && <Loader2 size={15} className="spin" aria-hidden="true" />}
          Save address
        </button>
      </div>
    </form>
  )
}

/* ------------------------------------------------------------------ */
/* AddressCard – single address display row                             */
/* ------------------------------------------------------------------ */
function AddressCard({ addr, onEdit, onDelete, deleting }) {
  const lines = [
    addr.line1,
    addr.line2,
    [addr.city, addr.state, addr.pincode].filter(Boolean).join(', '),
    addr.country,
  ].filter(Boolean)

  return (
    <div className={`addr-card${addr.default ? ' addr-card--default' : ''}`}>
      <div className="addr-card__body">
        <p className="addr-card__name">
          {addr.default && <Star size={13} className="addr-card__star" aria-label="Default" />}
          {addr.fullName}
          {addr.label && <span className="addr-card__label">{addr.label}</span>}
        </p>
        <p className="addr-card__phone">{addr.phone}</p>
        {lines.map((l, i) => <p key={i} className="addr-card__line">{l}</p>)}
      </div>
      <div className="addr-card__actions">
        <button className="addr-card__btn" onClick={onEdit} aria-label="Edit address">
          <Pencil size={15} />
        </button>
        <button
          className="addr-card__btn addr-card__btn--del"
          onClick={onDelete}
          disabled={deleting}
          aria-label="Delete address"
        >
          {deleting ? <Loader2 size={15} className="spin" /> : <Trash2 size={15} />}
        </button>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* AddressesSection – card rendered inside Account                      */
/* ------------------------------------------------------------------ */
function AddressesSection() {
  const { data: addresses = [], isLoading } = useAddresses()
  const createAddr  = useCreateAddress()
  const updateAddr  = useUpdateAddress()
  const deleteAddr  = useDeleteAddress()

  const [mode, setMode] = useState(null)   // null | 'new' | { editing: addr }
  const [deletingId, setDeletingId] = useState(null)

  const handleSave = async (values) => {
    if (mode === 'new') {
      await createAddr.mutateAsync(values)
    } else {
      await updateAddr.mutateAsync({ addressId: mode.editing.addressId, ...values })
    }
    setMode(null)
  }

  const handleDelete = async (addressId) => {
    setDeletingId(addressId)
    try {
      await deleteAddr.mutateAsync(addressId)
    } finally {
      setDeletingId(null)
    }
  }

  const saving = createAddr.isPending || updateAddr.isPending

  return (
    <div className="account-card">
      <div className="account-card__header account-card__header--simple">
        <MapPin size={18} aria-hidden="true" />
        <h2 className="account-card__name">Saved addresses</h2>
        {mode === null && (
          <button
            className="btn btn--ghost btn--sm addr-add-btn"
            onClick={() => setMode('new')}
          >
            <Plus size={15} aria-hidden="true" /> Add address
          </button>
        )}
      </div>

      {isLoading && <p className="addr-empty">Loading…</p>}

      {!isLoading && addresses.length === 0 && mode === null && (
        <p className="addr-empty">No saved addresses yet. Add one to speed up checkout.</p>
      )}

      {!isLoading && addresses.map((addr) =>
        mode?.editing?.addressId === addr.addressId ? (
          <AddressForm
            key={addr.addressId}
            initial={{
              label: addr.label || '',
              fullName: addr.fullName,
              phone: addr.phone,
              line1: addr.line1,
              line2: addr.line2 || '',
              city: addr.city,
              state: addr.state,
              pincode: addr.pincode,
              country: addr.country,
              isDefault: addr.default,
            }}
            onSave={handleSave}
            onCancel={() => setMode(null)}
            saving={saving}
          />
        ) : (
          <AddressCard
            key={addr.addressId}
            addr={addr}
            onEdit={() => setMode({ editing: addr })}
            onDelete={() => handleDelete(addr.addressId)}
            deleting={deletingId === addr.addressId}
          />
        )
      )}

      {mode === 'new' && (
        <AddressForm
          onSave={handleSave}
          onCancel={() => setMode(null)}
          saving={saving}
        />
      )}
    </div>
  )
}

const pwSchema = z
  .object({
    oldPassword: z.string().min(1, 'Current password is required'),
    newPassword: z.string().min(6, 'At least 6 characters'),
    confirmPassword: z.string().min(1, 'Please confirm the new password'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'New passwords do not match',
  })
  .refine((v) => v.oldPassword !== v.newPassword, {
    path: ['newPassword'],
    message: 'New password must be different from the current one',
  })

function formatDate(value) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleDateString(undefined, {
      year: 'numeric', month: 'long', day: 'numeric',
    })
  } catch {
    return String(value)
  }
}

function fullName(profile) {
  const fn = (profile?.firstName || '').trim()
  const ln = (profile?.lastName || '').trim()
  const combined = `${fn} ${ln}`.trim()
  return combined || '-'
}

function initials(profile) {
  const fn = profile?.firstName?.[0] || ''
  const ln = profile?.lastName?.[0] || ''
  const fromName = (fn + ln).toUpperCase()
  if (fromName) return fromName
  const email = profile?.email || ''
  return email.slice(0, 2).toUpperCase() || 'ME'
}

export default function Account() {
  const authUser = useAuthStore((s) => s.user)
  const { data: profile, isLoading, isError, error } = useMyProfile()
  const changePw = useChangePassword()
  const resendVerify = useResendVerification()

  const [showOld, setShowOld] = useState(false)
  const [showNew, setShowNew] = useState(false)

  const handleResendVerification = async () => {
    try {
      await resendVerify.mutateAsync()
      toast.success('Verification email sent. Check your inbox.')
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.message ||
        'Could not send verification email'
      toast.error(msg)
    }
  }

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(pwSchema),
    defaultValues: { oldPassword: '', newPassword: '', confirmPassword: '' },
  })

  const onSubmit = async (values) => {
    try {
      await changePw.mutateAsync({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      toast.success('Password updated')
      reset()
    } catch (err) {
      const msg =
        err?.response?.data?.message ||
        err?.message ||
        'Could not update password'
      toast.error(msg)
    }
  }

  if (isLoading) {
    return (
      <section className="account stack">
        <h1 className="account__title">My account</h1>
        <div className="account__skeleton">Loading your profile…</div>
      </section>
    )
  }

  if (isError) {
    return (
      <section className="account stack">
        <h1 className="account__title">My account</h1>
        <div className="account__error">
          {error?.response?.data?.message ||
            error?.message ||
            'Could not load profile'}
        </div>
      </section>
    )
  }

  const isAdmin = profile?.admin || authUser?.roles?.includes('admin')

  return (
    <section className="account stack">
      <h1 className="account__title">My account</h1>

      {/* ---------- Profile card ---------- */}
      <div className="account-card">
        <div className="account-card__header">
          <div className="account-avatar" aria-hidden="true">
            {initials(profile)}
          </div>
          <div className="account-card__heading">
            <h2 className="account-card__name">{fullName(profile)}</h2>
            <p className="account-card__email">
              <Mail size={14} aria-hidden="true" />
              <span>{profile?.email || '-'}</span>
            </p>
          </div>
          <div className="account-card__badges">
            {profile?.emailVerified && (
              <span className="account-badge account-badge--verified">
                <MailCheck size={14} aria-hidden="true" />
                Verified
              </span>
            )}
            {isAdmin && (
              <span className="account-badge account-badge--admin">
                <ShieldCheck size={14} aria-hidden="true" />
                Admin
              </span>
            )}
          </div>
        </div>

        <dl className="account-meta">
          <div className="account-meta__row">
            <dt><UserIcon size={14} aria-hidden="true" />Name</dt>
            <dd>{fullName(profile)}</dd>
          </div>
          <div className="account-meta__row">
            <dt><Mail size={14} aria-hidden="true" />Email</dt>
            <dd>{profile?.email || '-'}</dd>
          </div>
          <div className="account-meta__row">
            <dt><ShieldCheck size={14} aria-hidden="true" />Status</dt>
            <dd>
              <span
                className={`account-status account-status--${(profile?.status || 'unknown').toLowerCase()}`}
              >
                {profile?.status || 'UNKNOWN'}
              </span>
            </dd>
          </div>
     
        </dl>
      </div>

      {/* ---------- Email verification card (only when NOT verified) ---------- */}
      {!profile?.emailVerified && (
        <div className="account-card">
          <div className="account-card__header account-card__header--simple">
            <MailWarning size={18} aria-hidden="true" />
            <h2 className="account-card__name">Email verification</h2>
            <span
              className="account-badge account-badge--unverified"
              style={{ marginLeft: 'auto' }}
            >
              Not verified
            </span>
          </div>
          <p className="account-card__hint">
            Please verify your email address to place orders. We sent a
            verification link to <strong>{profile?.email}</strong> when you
            signed up &mdash; if you didn&rsquo;t receive it (or it&rsquo;s
            expired), we can send a fresh one.
          </p>
          <div className="account-form__actions">
            <button
              type="button"
              className="btn btn--primary"
              onClick={handleResendVerification}
              disabled={resendVerify.isPending}
            >
              {resendVerify.isPending && (
                <Loader2 size={16} className="spin" aria-hidden="true" />
              )}
              Resend verification email
            </button>
          </div>
        </div>
      )}

      {/* ---------- Saved addresses ---------- */}
      <AddressesSection />

      {/* ---------- Password card ---------- */}
      <div className="account-card">
        <div className="account-card__header account-card__header--simple">
          <KeyRound size={18} aria-hidden="true" />
          <h2 className="account-card__name">Change password</h2>
        </div>

        <form
          className="account-form"
          onSubmit={handleSubmit(onSubmit)}
          noValidate
        >
          <div className="form-field">
            <label htmlFor="oldPassword">Current password</label>
            <div className="form-input-wrap">
              <input
                id="oldPassword"
                type={showOld ? 'text' : 'password'}
                autoComplete="current-password"
                {...register('oldPassword')}
                aria-invalid={!!errors.oldPassword || undefined}
              />
              <button
                type="button"
                className="form-input-toggle"
                onClick={() => setShowOld((v) => !v)}
                aria-label={showOld ? 'Hide password' : 'Show password'}
              >
                {showOld ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.oldPassword && (
              <p className="form-error">{errors.oldPassword.message}</p>
            )}
          </div>

          <div className="form-field">
            <label htmlFor="newPassword">New password</label>
            <div className="form-input-wrap">
              <input
                id="newPassword"
                type={showNew ? 'text' : 'password'}
                autoComplete="new-password"
                {...register('newPassword')}
                aria-invalid={!!errors.newPassword || undefined}
              />
              <button
                type="button"
                className="form-input-toggle"
                onClick={() => setShowNew((v) => !v)}
                aria-label={showNew ? 'Hide password' : 'Show password'}
              >
                {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            {errors.newPassword && (
              <p className="form-error">{errors.newPassword.message}</p>
            )}
            <p className="form-hint">Minimum 6 characters.</p>
          </div>

          <div className="form-field">
            <label htmlFor="confirmPassword">Confirm new password</label>
            <input
              id="confirmPassword"
              type={showNew ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('confirmPassword')}
              aria-invalid={!!errors.confirmPassword || undefined}
            />
            {errors.confirmPassword && (
              <p className="form-error">{errors.confirmPassword.message}</p>
            )}
          </div>

          <div className="account-form__actions">
            <button
              type="submit"
              className="btn btn--primary"
              disabled={isSubmitting || changePw.isPending}
            >
              {(isSubmitting || changePw.isPending) && (
                <Loader2 size={16} className="spin" aria-hidden="true" />
              )}
              Update password
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}
