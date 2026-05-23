/* ------------------------------------------------------------------
 * Products data hooks (React Query).
 *
 * Backend contract (ProductController):
 *   GET /api/product                       -> all (paginated)
 *   GET /api/product?q=&category=&minPrice=&maxPrice=&inStock=&sort=
 *   GET /api/product?productId=<uuid>      -> single product
 *   GET /api/product?productId=<uuid>&related=true -> recommendations
 *
 * The list endpoint also accepts `limit` (1-100, default 10) and
 * `offset` (>=0, default 0).
 *
 * NOTE: backend currently does not return a total count, so we use
 * "load more" pagination (fetch limit+1 to detect if more exist).
 * ------------------------------------------------------------------ */
import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { api } from './api'

const PAGE_SIZE = 12

/** Build a stable cache key from the filters object. */
function filtersKey(f) {
  // `categories` is an array of lowercase names. We normalize +
  // sort so that ['a','b'] and ['b','a'] hit the same cache slot.
  const rawCats = Array.isArray(f.categories)
    ? f.categories
    : f.category
      ? [f.category]
      : []
  const categories = Array.from(
    new Set(rawCats.map((c) => String(c).trim()).filter(Boolean)),
  ).sort()
  return {
    q: f.q || '',
    categories,
    minPrice: f.minPrice ?? '',
    maxPrice: f.maxPrice ?? '',
    inStock: !!f.inStock,
    sort: f.sort || '',
  }
}

async function fetchProductsPage({ pageParam = 0, filters }) {
  const params = new URLSearchParams()
  params.set('limit', String(PAGE_SIZE))
  params.set('offset', String(pageParam))
  if (filters.q) params.set('q', filters.q)
  // Multi-category: send each selected category as a repeated
  // `?category=` param. The backend reads via getParameterValues
  // and ORs them together.
  for (const c of filters.categories || []) {
    params.append('category', c)
  }
  if (filters.minPrice != null && filters.minPrice !== '')
    params.set('minPrice', String(filters.minPrice))
  if (filters.maxPrice != null && filters.maxPrice !== '')
    params.set('maxPrice', String(filters.maxPrice))
  if (filters.inStock) params.set('inStock', 'true')
  if (filters.sort) params.set('sort', filters.sort)

  const res = await api.get(`/api/product?${params.toString()}`, {
    _withAuth: false,
  })
  // Backend wraps payload in ApiResponse { success, message, data }.
  return res.data?.data || []
}

/**
 * Paginated product list with filter awareness.
 *
 * Returns the standard react-query infinite shape plus a flat
 * `products` array for convenience.
 */
export function useProducts(filters) {
  const key = filtersKey(filters)
  const q = useInfiniteQuery({
    queryKey: ['products', key],
    queryFn: ({ pageParam }) => fetchProductsPage({ pageParam, filters: key }),
    initialPageParam: 0,
    // If the last page returned a full page, assume there might be more.
    getNextPageParam: (lastPage, allPages) =>
      lastPage.length < PAGE_SIZE
        ? undefined
        : allPages.reduce((n, p) => n + p.length, 0),
    staleTime: 60_000,
  })
  const products = q.data?.pages.flat() ?? []
  return { ...q, products }
}

/**
 * Distinct category list for the catalogue filter sidebar.
 *
 * Backend: GET /api/category -> ApiResponse { data: string[] }.
 * Returned categories are already trimmed and sorted server-side;
 * we just normalize the empty-array case so consumers can render
 * `categories.map(...)` unconditionally.
 */
export function useCategories() {
  return useQuery({
    queryKey: ['product-categories'],
    queryFn: async () => {
      const res = await api.get('/api/category', { _withAuth: false })
      const list = res.data?.data
      return Array.isArray(list) ? list : []
    },
    staleTime: 5 * 60_000,
  })
}

/** Single product fetch (for the detail page later). */
export function useProduct(productId) {
  return useQuery({
    queryKey: ['product', productId],
    enabled: !!productId,
    queryFn: async () => {
      const res = await api.get(
        `/api/product?productId=${encodeURIComponent(productId)}`,
        { _withAuth: false },
      )
      return res.data?.data
    },
    staleTime: 60_000,
  })
}

/**
 * Recommendation rails for the detail page.
 *
 * Backend returns a map:
 *   { sameCategory: Product[], alsoBought: Product[] }
 *
 * We normalize to `{ sameCategory, alsoBought }` with empty-array
 * defaults so consumers never have to null-check.
 */
export function useRelatedProducts(productId, limit = 8) {
  return useQuery({
    queryKey: ['product', productId, 'related', limit],
    enabled: !!productId,
    queryFn: async () => {
      const res = await api.get(
        `/api/product?productId=${encodeURIComponent(productId)}&related=true&limit=${limit}`,
        { _withAuth: false },
      )
      const data = res.data?.data || {}
      return {
        sameCategory: Array.isArray(data.sameCategory) ? data.sameCategory : [],
        alsoBought:   Array.isArray(data.alsoBought)   ? data.alsoBought   : [],
      }
    },
    staleTime: 60_000,
  })
}

export const PRODUCT_PAGE_SIZE = PAGE_SIZE
