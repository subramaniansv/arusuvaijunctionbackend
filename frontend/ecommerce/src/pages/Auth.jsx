/**
 * Auth - one page for both sign-in and sign-up.
 *
 * The mode (`login` | `register`) lives in URL state so that:
 *   - `/login` and `/register` both render this component (existing
 *     redirects like ProtectedRoute -> `/login` keep working)
 *   - browser back/forward navigates between the two modes
 *   - Toggling via the in-page link uses `navigate(..., { replace })`
 *     to swap modes without polluting history.
 *
 * Backend contract (matches AuthController + UserConverterUtil):
 *   POST /auth?isLogin=true   { email, passwordHash }
 *   POST /auth?isLogin=false  { email, passwordHash, firstName, lastName }
 * The backend's `passwordHash` field is the raw password - the server
 * hashes it. Field name is unfortunate but locked by the existing DTO.
 */
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Sparkles, Leaf, Shield, Heart } from 'lucide-react'

import { api } from '../lib/api'
import { useAuthStore } from '../stores/authStore'
import {
  Button,
  Input,
  PasswordInput,
  Alert,
} from '../components'
import './Auth.css'

export default function Auth() {
  const location = useLocation()
  const navigate = useNavigate()
  const setTokens = useAuthStore((s) => s.setTokens)

  const isRegister = location.pathname === '/register'
  const mode = isRegister ? 'register' : 'login'

  const [form, setForm] = useState({
    email: '',
    password: '',
    confirm: '',
    firstName: '',
    lastName: '',
  })
  const [errors, setErrors] = useState({})
  const [serverErr, setServerErr] = useState(null)
  const [busy, setBusy] = useState(false)

  const set = (field) => (e) =>
    setForm((f) => ({ ...f, [field]: e.target.value }))

  const validate = () => {
    const e = {}
    if (!form.email.trim()) e.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
      e.email = 'Enter a valid email'
    if (mode === 'register') {
      if (!form.password) e.password = 'Password is required'
      else if (form.password.length < 6)
        e.password = 'Password must be at least 6 characters'
      if (!form.firstName.trim()) e.firstName = 'First name is required'
      else if (form.firstName.trim().length < 3)
        e.firstName = 'First name must be at least 3 characters'
      if (!form.lastName.trim()) e.lastName = 'Last name is required'
      if (form.confirm !== form.password)
        e.confirm = 'Passwords do not match'
    }
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const onSubmit = async (ev) => {
    ev.preventDefault()
    setServerErr(null)
    if (!validate()) return
    setBusy(true)
    try {
      const body =
        mode === 'register'
          ? {
              email: form.email,
              passwordHash: form.password,
              firstName: form.firstName,
              lastName: form.lastName,
            }
          : { email: form.email, passwordHash: form.password }

      const res = await api.post(
        `/auth?isLogin=${mode === 'login'}`,
        body,
        { _withAuth: false },
      )
      const data = res.data?.data
      if (!data?.accessToken) throw new Error('auth response missing tokens')
      setTokens({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
      })
      const to = location.state?.from?.pathname || '/'
      navigate(to, { replace: true })
    } catch (e2) {
      setServerErr(
        e2.response?.data?.message ||
          e2.message ||
          (mode === 'login' ? 'Sign in failed' : 'Registration failed'),
      )
    } finally {
      setBusy(false)
    }
  }

  const swap = (e) => {
    e.preventDefault()
    // Replace, not push -- the user is just flipping a tab, not
    // navigating into deeper state. Carry `from` so the post-auth
    // redirect target survives the mode switch.
    navigate(isRegister ? '/login' : '/register', {
      replace: true,
      state: location.state,
    })
    setErrors({})
    setServerErr(null)
  }

  return (
    <div className="auth">
      {/* ---------- brand side ---------- */}
      <aside className="auth__brand" aria-hidden="true">
        <div className="auth__brand-inner">
          <Link to="/" className="auth__logo">
            Arusuvai
          </Link>
          <h2 className="auth__brand-title">
            Homemade Tamil snacks,<br />delivered fresh.
          </h2>
          <p className="auth__brand-sub">
            Sign in to track orders, save favourites, and check out faster.
          </p>
          <ul className="auth__perks">
            <li><Leaf size={18} /> 100% homemade, no preservatives</li>
            <li><Shield size={18} /> Secure checkout</li>
            <li><Heart size={18} /> Loved by 10k+ customers</li>
          </ul>
        </div>
        <div className="auth__blob auth__blob--yellow" />
        <div className="auth__blob auth__blob--green" />
      </aside>

      {/* ---------- form side ---------- */}
      <main className="auth__panel">
        <div className="auth__form-wrap">
          <div className="auth__eyebrow">
            <Sparkles size={14} />
            {mode === 'login' ? 'Welcome back' : 'Join Arusuvai'}
          </div>
          <h1 className="auth__title">
            {mode === 'login' ? 'Sign in to your account' : 'Create your account'}
          </h1>
          <p className="auth__sub">
            {mode === 'login'
              ? 'Enter your credentials to continue.'
              : 'A few quick details and you are set.'}
          </p>

          {serverErr && (
            <Alert
              variant="danger"
              title={mode === 'login' ? "Couldn't sign in" : "Couldn't register"}
              className="auth__alert"
            >
              {serverErr}
            </Alert>
          )}

          <form className="auth__form" onSubmit={onSubmit} noValidate>
            {mode === 'register' && (
              <div className="auth__row">
                <Input
                  label="First name"
                  type="text"
                  autoComplete="given-name"
                  value={form.firstName}
                  onChange={set('firstName')}
                  error={errors.firstName}
                  required
                />
                <Input
                  label="Last name"
                  type="text"
                  autoComplete="family-name"
                  value={form.lastName}
                  onChange={set('lastName')}
                  error={errors.lastName}
                  required
                />
              </div>
            )}

            <Input
              label="Email"
              type="email"
              autoComplete="email"
              value={form.email}
              onChange={set('email')}
              error={errors.email}
              required
            />

            <PasswordInput
              label="Password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={form.password}
              onChange={set('password')}
              error={errors.password}
              required
            />

            {mode === 'register' && (
              <PasswordInput
                label="Confirm password"
                autoComplete="new-password"
                value={form.confirm}
                onChange={set('confirm')}
                error={errors.confirm}
                required
              />
            )}

            {mode === 'login' && (
              <div className="auth__row-meta">
                {/* Forgot-password flow isn't wired yet -- placeholder
                 * link kept disabled-looking so users don't click it
                 * and hit a dead end. */}
                <span className="auth__forgot" aria-disabled>
                  Forgot password?
                </span>
              </div>
            )}

            <Button
              type="submit"
              size="lg"
              fullWidth
              loading={busy}
            >
              {mode === 'login' ? 'Sign in' : 'Create account'}
            </Button>
          </form>

          <p className="auth__swap">
            {mode === 'login'
              ? "Don't have an account?"
              : 'Already have an account?'}{' '}
            <a href={mode === 'login' ? '/register' : '/login'} onClick={swap}>
              {mode === 'login' ? 'Create one' : 'Sign in'}
            </a>
          </p>
        </div>
      </main>
    </div>
  )
}
