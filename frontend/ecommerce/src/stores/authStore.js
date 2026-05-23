/**
 * Auth store (Zustand).
 *
 * Tokens come from the backend:
 *   - access token  : ~1 day TTL (sent as Bearer on every API call)
 *   - refresh token : ~7 day TTL (used to mint a new access token
 *                     when the current one expires)
 *
 * Persistence rationale:
 *   - We keep both tokens in localStorage so a page reload doesn't
 *     log the user out. This is the same trust boundary as the JWT
 *     itself (an attacker with XSS can already act as the user).
 *   - If you ever decide to harden against XSS, move the refresh
 *     token into a httpOnly cookie issued by the backend; the rest
 *     of this store stays the same.
 *
 * Decoding rationale:
 *   - We decode the JWT once on set() to extract `exp` (seconds
 *     since epoch) so the axios interceptor can preemptively
 *     refresh before the server sends a 401 - cuts one round-trip
 *     off every "first call after token expiry" path.
 */
import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'

const STORAGE_KEY = 'arusuvai-auth'

/** Decode a JWT payload without verifying the signature. */
function decodeJwt(token) {
  if (!token || typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4)
    return JSON.parse(atob(padded))
  } catch {
    return null
  }
}

export const useAuthStore = create(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,           // { userId, email, roles[] } - derived from JWT
      accessExp: 0,         // unix seconds; 0 means unknown/expired

      /** Called by login/register success and by refresh. */
      setTokens: ({ accessToken, refreshToken }) => {
        const claims = decodeJwt(accessToken) || {}
        // Backend sends roles as objects ({id, name, description, permissions}),
        // but the rest of the app treats roles as a flat list of lowercase
        // names. Normalize here so guards / nav can just do
        // `roles.includes('admin')`.
        const roles = Array.isArray(claims.roles)
          ? claims.roles
              .map((r) =>
                typeof r === 'string'
                  ? r
                  : r && typeof r.name === 'string'
                    ? r.name
                    : null,
              )
              .filter(Boolean)
              .map((n) => n.toLowerCase())
          : []
        set({
          accessToken,
          // backend doesn't always return a new refresh on refresh
          refreshToken: refreshToken || get().refreshToken,
          accessExp: claims.exp || 0,
          user: claims.sub
            ? {
                userId: claims.sub,
                email: claims.email || null,
                roles,
              }
            : get().user,
        })
      },

      clear: () => set({
        accessToken: null,
        refreshToken: null,
        accessExp: 0,
        user: null,
      }),

      /** Convenience selectors. */
      isAuthenticated: () => !!get().accessToken,
      hasRole: (role) => (get().user?.roles || []).includes(role),
      /**
       * True when the access token is gone or within `leewaySec`
       * seconds of expiring. The interceptor uses this to refresh
       * proactively.
       */
      isAccessExpiring: (leewaySec = 30) => {
        const exp = get().accessExp
        if (!exp) return true
        const nowSec = Math.floor(Date.now() / 1000)
        return exp - nowSec <= leewaySec
      },
    }),
    {
      name: STORAGE_KEY,
      storage: createJSONStorage(() => localStorage),
      // Bump the version whenever the persisted shape changes so old
      // sessions migrate cleanly instead of breaking guards.
      version: 2,
      migrate: (persisted, _version) => {
        if (!persisted || !persisted.user) return persisted
        const raw = persisted.user.roles
        const roles = Array.isArray(raw)
          ? raw
              .map((r) =>
                typeof r === 'string'
                  ? r
                  : r && typeof r.name === 'string'
                    ? r.name
                    : null,
              )
              .filter(Boolean)
              .map((n) => n.toLowerCase())
          : []
        return {
          ...persisted,
          user: { ...persisted.user, roles },
        }
      },
      // Only persist what's needed to rehydrate auth - never persist
      // function references.
      partialize: (s) => ({
        accessToken: s.accessToken,
        refreshToken: s.refreshToken,
        accessExp: s.accessExp,
        user: s.user,
      }),
    }
  )
)
