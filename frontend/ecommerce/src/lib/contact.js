/**
 * Contact form API helpers.
 *
 * Public endpoints:
 *   POST   /api/contact                      submit message (anonymous OK)
 *
 * Admin-only endpoints (used by the admin console):
 *   GET    /api/contact?limit=&offset=&status=
 *   PATCH  /api/contact?messageId=&status=
 *   DELETE /api/contact?messageId=
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

export async function submitContactMessage(payload) {
  const { data } = await api.post('/api/contact', payload)
  return data.data
}

export function useSubmitContact() {
  return useMutation({ mutationFn: submitContactMessage })
}

export function useAdminContactMessages({ limit = 20, offset = 0, status = '' } = {}) {
  return useQuery({
    queryKey: ['admin', 'contact', { limit, offset, status }],
    queryFn: async () => {
      const params = new URLSearchParams()
      params.set('limit', String(limit))
      params.set('offset', String(offset))
      if (status) params.set('status', status)
      const { data } = await api.get(`/api/contact?${params.toString()}`)
      return data.data
    },
    keepPreviousData: true,
  })
}

export function useUpdateContactStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ messageId, status }) => {
      const params = new URLSearchParams({ messageId, status })
      const { data } = await api.patch(`/api/contact?${params.toString()}`)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'contact'] }),
  })
}

export function useDeleteContact() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (messageId) => {
      const { data } = await api.delete(`/api/contact?messageId=${encodeURIComponent(messageId)}`)
      return data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'contact'] }),
  })
}
