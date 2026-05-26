/* ------------------------------------------------------------------
 * Admin data hooks (React Query).
 *
 * Backend contract:
 *   GET  /api/admin?type=product&limit&offset    -> all products (paged)
 *   GET  /api/admin?type=order&limit&offset      -> all orders (paged)
 *   PUT  /api/admin?orderId=<uuid>&status=<>     -> update order status
 *
 *   POST /api/product (multipart)                -> create product
 *      form fields:
 *        product : JSON string of Product (name, description, category,
 *                  ingredients, price, stockQuantity)
 *        images  : 0..N image files (first file is treated as primary)
 *   PUT  /api/product (JSON Product)             -> full update (stock too)
 *
 *   GET  /api/user                               -> list users
 *   GET  /api/user?userId=<uuid>                 -> one user
 *   PUT  /api/user?userId=<uuid>&status=<>       -> update user status
 *
 * All endpoints require an Admin JWT - the axios interceptor attaches
 * it automatically. ApiResponse { success, message, data } is unwrapped
 * to `data` so callers get plain arrays/objects.
 * ------------------------------------------------------------------ */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'

const PAGE_SIZE = 50

// ---------------- products ----------------

/** Admin product list. Single page (no infinite scroll) sized for tables. */
export function useAdminProducts({ limit = PAGE_SIZE, offset = 0 } = {}) {
  return useQuery({
    queryKey: ['admin', 'products', { limit, offset }],
    queryFn: async () => {
      const res = await api.get(
        `/api/admin?type=product&limit=${limit}&offset=${offset}`,
      )
      return res.data?.data || []
    },
    staleTime: 30_000,
  })
}

/**
 * Create a product. Backend expects a multipart form with a JSON
 * `product` field plus optional image files. We let the browser set
 * the Content-Type header so the boundary is correct.
 */
export function useCreateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ product, images = [] }) => {
      const form = new FormData()
      form.append('product', JSON.stringify(product))
      for (const file of images) {
        form.append('images', file)
      }
      const res = await api.post('/api/product', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return res.data?.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['product-categories'] })
    },
  })
}

/**
 * Full product update. Used for stock edits as well - we merge the
 * current product with the patch so the backend's full-replace PUT
 * doesn't accidentally null out fields the admin didn't touch.
 */
export function useUpdateProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (product) => {
      const res = await api.put('/api/product', product)
      return res.data?.data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      if (vars?.id) {
        qc.invalidateQueries({ queryKey: ['admin', 'product', vars.id] })
      }
    },
  })
}

/** Fetch a single product (with images, variants, reviews) for the edit page. */
export function useAdminProduct(productId) {
  return useQuery({
    queryKey: ['admin', 'product', productId],
    queryFn: async () => {
      const res = await api.get(`/api/product?productId=${productId}`)
      return res.data?.data
    },
    enabled: !!productId,
    staleTime: 10_000,
  })
}

/** Permanently delete a product (and its images). */
export function useDeleteProduct() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (productId) => {
      const res = await api.delete(`/api/product?productId=${productId}`)
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['product-categories'] })
    },
  })
}

// ---------------- product images ----------------

/**
 * Upload a single image and attach it to an existing product.
 * The first image to land on a product is auto-promoted to primary
 * server-side.
 */
export function useAddProductImage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, file }) => {
      const form = new FormData()
      form.append('productId', productId)
      form.append('image', file)
      const res = await api.post('/api/product/image', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return res.data?.data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
    },
  })
}

/** Delete a single product image (object + DB row). */
export function useDeleteProductImage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ imageId /* , productId */ }) => {
      const res = await api.delete(`/api/product/image?imageId=${imageId}`)
      return res.data
    },
    onSuccess: (_data, vars) => {
      if (vars?.productId) {
        qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
      }
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
    },
  })
}

/** Promote an image to primary. */
export function useSetPrimaryImage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ productId, imageId }) => {
      const qs = new URLSearchParams({
        productId,
        imageId,
        primary: 'true',
      })
      const res = await api.put(`/api/product/image?${qs.toString()}`)
      return res.data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
      qc.invalidateQueries({ queryKey: ['admin', 'products'] })
      qc.invalidateQueries({ queryKey: ['products'] })
    },
  })
}

// ---------------- product variants ----------------

/** List variants for a product. Admin can include inactive (soft-deleted). */
export function useProductVariants(productId, { includeInactive = false } = {}) {
  return useQuery({
    queryKey: ['admin', 'variants', productId, { includeInactive }],
    queryFn: async () => {
      const qs = new URLSearchParams({ productId })
      if (includeInactive) qs.set('includeInactive', 'true')
      const res = await api.get(`/api/product/variant?${qs.toString()}`)
      return res.data?.data || []
    },
    enabled: !!productId,
    staleTime: 10_000,
  })
}

export function useCreateVariant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (variant) => {
      const res = await api.post('/api/product/variant', variant)
      return res.data?.data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'variants', vars.productId] })
      qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
    },
  })
}

export function useUpdateVariant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ variantId, productId: _pid, ...patch }) => {
      const res = await api.put(
        `/api/product/variant?variantId=${variantId}`,
        patch,
      )
      return res.data?.data
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ['admin', 'variants', vars.productId] })
      qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
    },
  })
}

export function useDeleteVariant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ variantId /* , productId */ }) => {
      const res = await api.delete(
        `/api/product/variant?variantId=${variantId}`,
      )
      return res.data
    },
    onSuccess: (_data, vars) => {
      if (vars?.productId) {
        qc.invalidateQueries({ queryKey: ['admin', 'variants', vars.productId] })
        qc.invalidateQueries({ queryKey: ['admin', 'product', vars.productId] })
      }
    },
  })
}

// ---------------- orders ----------------

export function useAdminOrders({ limit = PAGE_SIZE, offset = 0 } = {}) {
  return useQuery({
    queryKey: ['admin', 'orders', { limit, offset }],
    queryFn: async () => {
      const res = await api.get(
        `/api/admin?type=order&limit=${limit}&offset=${offset}`,
      )
      return res.data?.data || []
    },
    staleTime: 15_000,
  })
}

export function useUpdateOrderStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ orderId, status }) => {
      const qs = new URLSearchParams({ orderId, status })
      const res = await api.put(`/api/admin?${qs.toString()}`)
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'orders'] })
      qc.invalidateQueries({ queryKey: ['orders'] })
    },
  })
}

// ---------------- users ----------------

export function useAdminUsers() {
  return useQuery({
    queryKey: ['admin', 'users'],
    queryFn: async () => {
      const res = await api.get('/api/user')
      const list = res.data?.data
      return Array.isArray(list) ? list : []
    },
    staleTime: 30_000,
  })
}

export function useUpdateUserStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ userId, status }) => {
      const qs = new URLSearchParams({ userId, status })
      const res = await api.put(`/api/user?${qs.toString()}`)
      return res.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })
}

export const ORDER_STATUSES = [
  'PAYMENT_PENDING',
  'PAID',
  'PAYMENT_FAILED',
  'PENDING',
  'CONFIRMED',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'REFUNDED',
]

export const USER_STATUSES = ['ACTIVE', 'INACTIVE', 'SUSPENDED', 'DEAD']
