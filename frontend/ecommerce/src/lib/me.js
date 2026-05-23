/**
 * Self-service profile + password hooks.
 *
 * Backed by:
 *   GET /api/me                          -> own profile (passwordHash stripped server-side)
 *   PUT /api/me  { oldPassword, newPassword }   -> change own password
 *
 * The user identity is taken from the JWT on the server; no userId is
 * sent from the client, so a logged-in user can only ever touch their
 * own account.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

const ME_KEY = ['me']

export function useMyProfile() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async () => {
      const res = await api.get('/api/me')
      return res.data?.data
    },
    staleTime: 60_000,
  })
}

/**
 * Change own password.
 *
 * Surfaces backend error messages verbatim so the form can show
 * things like "current password is incorrect" or "newPassword must
 * be at least 6 characters" without having to map status codes.
 */
export function useChangePassword() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ oldPassword, newPassword }) => {
      const res = await api.put('/api/me', { oldPassword, newPassword })
      return res.data
    },
    onSuccess: () => {
      // Profile data hasn't changed but invalidate anyway so any
      // dependent UI re-fetches.
      qc.invalidateQueries({ queryKey: ME_KEY })
    },
  })
}

/**
 * Resend the email-verification link to the authenticated caller.
 * Backed by POST /api/email-verify/resend - the server uses the JWT
 * to know who's asking, no body needed.
 */
export function useResendVerification() {
  return useMutation({
    mutationFn: async () => {
      const res = await api.post('/api/email-verify/resend')
      return res.data
    },
  })
}
