/* ------------------------------------------------------------------
 * Saved address hooks (React Query).
 *
 * Backend contract (UserAddressController):
 *   GET    /api/address               -> list user's saved addresses
 *   POST   /api/address               -> create address
 *   PUT    /api/address?id=<uuid>     -> update address
 *   DELETE /api/address?id=<uuid>     -> delete address
 *
 * Address shape:
 *   { addressId, userId, label, fullName, phone,
 *     line1, line2, city, state, pincode, country,
 *     isDefault, createdAt, updatedAt }
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'

import { api } from './api'
import { useAuthStore } from '../stores/authStore'

const ADDRESSES_KEY = ['addresses']

/* ------------------------------------------------------------------
 * List
 * ------------------------------------------------------------------ */
export function useAddresses() {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  return useQuery({
    queryKey: ADDRESSES_KEY,
    enabled: isAuthed,
    queryFn: async () => {
      const res = await api.get('/api/address')
      return res.data?.data || []
    },
    staleTime: 60_000,
  })
}

/* ------------------------------------------------------------------
 * Create
 * ------------------------------------------------------------------ */
export function useCreateAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (address) => {
      const res = await api.post('/api/address', address)
      return res.data?.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ADDRESSES_KEY })
      toast.success('Address saved')
    },
    onError: (err) => {
      toast.error(
        err?.response?.data?.message || err?.message || 'Could not save address',
      )
    },
  })
}

/* ------------------------------------------------------------------
 * Update
 * ------------------------------------------------------------------ */
export function useUpdateAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ addressId, ...data }) => {
      const res = await api.put(
        `/api/address?id=${encodeURIComponent(addressId)}`,
        data,
      )
      return res.data?.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ADDRESSES_KEY })
      toast.success('Address updated')
    },
    onError: (err) => {
      toast.error(
        err?.response?.data?.message || err?.message || 'Could not update address',
      )
    },
  })
}

/* ------------------------------------------------------------------
 * Delete
 * ------------------------------------------------------------------ */
export function useDeleteAddress() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (addressId) => {
      await api.delete(`/api/address?id=${encodeURIComponent(addressId)}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ADDRESSES_KEY })
      toast.success('Address deleted')
    },
    onError: (err) => {
      toast.error(
        err?.response?.data?.message || err?.message || 'Could not delete address',
      )
    },
  })
}
