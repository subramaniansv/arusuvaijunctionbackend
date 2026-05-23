/**
 * Account page.
 *
 * Two cards:
 *   1. Profile     - read-only view of the authenticated caller's
 *                    own /api/me record (name, email, status,
 *                    member-since, admin badge).
 *   2. Password    - form to change the password via PUT /api/me.
 *                    Validation mirrors the backend policy
 *                    (min 6 chars, new != old, confirmation match)
 *                    so the user gets instant feedback.
 *
 * Server-side errors (e.g. "current password is incorrect") are
 * surfaced verbatim - the backend already returns user-friendly
 * messages.
 */
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  User as UserIcon, Mail, ShieldCheck, Calendar,
  Eye, EyeOff, KeyRound, Loader2, MailCheck, MailWarning,
} from 'lucide-react'

import { useMyProfile, useChangePassword, useResendVerification } from '../lib/me'
import { useAuthStore } from '../stores/authStore'
import './Account.css'

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
  if (!value) return '—'
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
  return combined || '—'
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
              <span>{profile?.email || '—'}</span>
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
            <dd>{profile?.email || '—'}</dd>
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
