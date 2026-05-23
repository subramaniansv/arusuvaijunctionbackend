/**
 * Axios client with auth + refresh handling.
 *
 * Flow on every request:
 *   1. If access token is missing or about to expire (<= 30s left),
 *      pre-emptively call the refresh endpoint before sending the
 *      original request. Saves the 401-retry round-trip.
 *   2. Otherwise just attach `Authorization: Bearer <token>`.
 *
 * Flow on 401 response:
 *   1. If we haven't already retried this request, call refresh.
 *   2. Re-issue the original request with the new access token.
 *   3. If refresh itself 401s (or we're out of refresh attempts),
 *      clear the auth store and let the router redirect to /login.
 *
 * A single in-flight refresh is shared across concurrent failing
 * requests using `refreshPromise` so we don't hit the server N times.
 */
import axios from 'axios'
import { useAuthStore } from '../stores/authStore'

// Vite proxies /arusuvai/* -> http://localhost:8080 in dev (see
// vite.config.js). In prod, set VITE_API_BASE to the public URL.
const baseURL = import.meta.env.VITE_API_BASE || '/arusuvai'

export const api = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})

// ------------------------------------------------------------------
// Refresh helpers
// ------------------------------------------------------------------
let refreshPromise = null

async function refreshAccessToken() {
  const { refreshToken, setTokens, clear } = useAuthStore.getState()
  if (!refreshToken) {
    clear()
    throw new Error('no refresh token')
  }

  // Use a bare axios call (NOT `api`) so we don't recurse through
  // the interceptors.
  try {
    const res = await axios.post(
      `${baseURL}/auth?isRefresh=true`,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } }
    )
    const data = res.data?.data
    if (!data?.accessToken) throw new Error('refresh response missing accessToken')
    setTokens({
      accessToken: data.accessToken,
      refreshToken: data.refreshToken, // may be undefined - store keeps the old one
    })
    return data.accessToken
  } catch (err) {
    clear()
    throw err
  }
}

/** Coalesce concurrent refreshes into one in-flight promise. */
function ensureFreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = refreshAccessToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

// ------------------------------------------------------------------
// Request interceptor: attach bearer, refresh proactively if expiring
// ------------------------------------------------------------------
api.interceptors.request.use(async (config) => {
  // Skip auth for the auth endpoint itself (login/register/refresh).
  if (config.url?.startsWith('/auth') && !config._withAuth) {
    return config
  }

  const { accessToken, refreshToken, isAccessExpiring } = useAuthStore.getState()

  // Need a token? Try to refresh first.
  if ((!accessToken || isAccessExpiring()) && refreshToken) {
    try {
      const fresh = await ensureFreshAccessToken()
      config.headers.Authorization = `Bearer ${fresh}`
      return config
    } catch {
      // fall through - request goes out without auth, server will 401
    }
  }

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// ------------------------------------------------------------------
// Response interceptor: on 401, refresh once and retry the request
// ------------------------------------------------------------------
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const status = error.response?.status

    if (status === 401 && !original._retried) {
      original._retried = true
      try {
        const fresh = await ensureFreshAccessToken()
        original.headers.Authorization = `Bearer ${fresh}`
        return api(original)
      } catch {
        // refresh failed - propagate the original 401
      }
    }
    return Promise.reject(error)
  }
)
