/**
 * Products page - public catalog.
 *
 * Filter state lives in the URL via `useSearchParams` so refresh,
 * share, and back/forward all just work. Supported query params:
 *   q          - free-text search
 *   category   - distinct value served by GET /api/category
 *   minPrice   - number
 *   maxPrice   - number
 *   inStock    - 'true' | absent
 *   sort       - 'price_asc' | 'price_desc' | 'newest' | 'rating'
 *
 * Data: `useProducts(filters)` is an infinite-query that pages in
 * results. The "Load more" button kicks off the next page.
 * Add-to-cart is stubbed (`console.log`) until the cart hook lands.
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Search, SlidersHorizontal, X, ShoppingBag } from 'lucide-react'

import {
  Container,
  Input,
  Select,
  Checkbox,
  Button,
  Badge,
  ProductCard,
  Skeleton,
  Alert,
  EmptyState,
  Tag,
} from '../components'
import { useProducts, useCategories } from '../lib/products'
import { useAddToCart } from '../lib/cart'
import Seo from '../components/Seo'
import { breadcrumbLd, BRAND } from '../lib/seo'
import { useAuthStore } from '../stores/authStore'
import './Products.css'

const SORT_OPTIONS = [
  { value: '',           label: 'Relevance' },
  { value: 'price_asc',  label: 'Price: low to high' },
  { value: 'price_desc', label: 'Price: high to low' },
  { value: 'newest',     label: 'Newest first' },
  { value: 'rating',     label: 'Highest rated' },
]

/* ---------------- search-param <-> filters helpers --------------- */
function readFilters(params) {
  // `category` can repeat in the URL (?category=a&category=b). We
  // also accept a single comma-separated value for share-link
  // friendliness. All values are lower-cased + deduped so the cache
  // key is stable regardless of order/case.
  const raw = params.getAll('category').flatMap((v) => v.split(','))
  const categories = Array.from(
    new Set(raw.map((c) => c.trim().toLowerCase()).filter(Boolean)),
  )
  return {
    q:        params.get('q') || '',
    categories,
    minPrice: params.get('minPrice') || '',
    maxPrice: params.get('maxPrice') || '',
    inStock:  params.get('inStock') === 'true',
    sort:     params.get('sort') || '',
  }
}

function writeFilters(prev, next) {
  const out = new URLSearchParams(prev)
  const setOrDel = (k, v) => {
    if (v === '' || v == null || v === false) out.delete(k)
    else out.set(k, String(v))
  }
  setOrDel('q', next.q)
  // Categories: clear then append each value so the URL contains
  // one `category=` entry per selected option.
  out.delete('category')
  for (const c of next.categories || []) {
    if (c) out.append('category', c)
  }
  setOrDel('minPrice', next.minPrice)
  setOrDel('maxPrice', next.maxPrice)
  setOrDel('inStock', next.inStock)
  setOrDel('sort', next.sort)
  return out
}

/* ============================ Page ============================= */
export default function Products() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => readFilters(searchParams), [searchParams])

  // Local search input -- debounced into the URL so we don't fire a
  // query on every keystroke.
  const [searchDraft, setSearchDraft] = useState(filters.q)
  useEffect(() => { setSearchDraft(filters.q) }, [filters.q])
  useEffect(() => {
    const id = setTimeout(() => {
      if (searchDraft !== filters.q) {
        setSearchParams((prev) =>
          writeFilters(prev, { ...readFilters(prev), q: searchDraft }),
        )
      }
    }, 300)
    return () => clearTimeout(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchDraft])

  const [filtersOpen, setFiltersOpen] = useState(false)

  // Lock body scroll while the mobile drawer is open.
  useEffect(() => {
    if (!filtersOpen) return
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [filtersOpen])

  const update = (patch) => {
    setSearchParams((prev) => writeFilters(prev, { ...readFilters(prev), ...patch }))
  }

  const clearAll = () => setSearchParams(new URLSearchParams())

  const {
    products,
    isLoading,
    isError,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    refetch,
  } = useProducts(filters)

  // Distinct categories from the backend (replaces the old static list).
  // While loading we render no rows -- the "All categories" radio is
  // always available so the filter remains usable.
  const { data: categories = [], isLoading: categoriesLoading } = useCategories()

  const activeChips = buildActiveChips(filters, update)

  /* ----------------------------------------------------------------
   * Infinite scroll via IntersectionObserver.
   * A sentinel <div> sits below the grid; when it scrolls into view
   * (with a 400px rootMargin so we prefetch just before the user
   * reaches the bottom) we fire `fetchNextPage()`. Guarded by
   * hasNextPage + !isFetchingNextPage so we never queue duplicate
   * fetches. Falls back gracefully if IntersectionObserver is not
   * available; the "Load more" button below still works.
   * ---------------------------------------------------------------- */
  const sentinelRef = useRef(null)
  useEffect(() => {
    const node = sentinelRef.current
    if (!node) return
    if (typeof IntersectionObserver === 'undefined') return
    if (!hasNextPage) return

    const io = new IntersectionObserver(
      (entries) => {
        const [entry] = entries
        if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage()
        }
      },
      { rootMargin: '400px 0px' },
    )
    io.observe(node)
    return () => io.disconnect()
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, products.length])

  // ---- add-to-cart wiring ----
  const addToCart = useAddToCart()
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const navigate = useNavigate()
  const handleAddToCart = (productId) => {
    if (!isAuthed) {
      navigate('/login', { state: { from: { pathname: '/products' } } })
      return
    }
    const product = products.find((p) => (p.productId || p.id) === productId)
    addToCart.mutate({ productId, quantity: 1, product })
  }

  return (
    <div className="products">
      <Seo
        title={
          filters.q
            ? `Search results for “${filters.q}”`
            : filters.category
              ? `${filters.category} - traditional Indian snacks`
              : 'Shop all snacks - traditional, sugar-free, protein-rich'
        }
        description={`Browse Arusuvai Junction\u2019s full range of traditional Indian snacks: murukku, laddoos, mixture, sweets and more. ${BRAND.tagline}`}
        path={filters.q ? '/products' : '/products'}
        noindex={Boolean(filters.q)}
        keywords={BRAND.defaultKeywords}
        jsonLd={breadcrumbLd([
          { name: 'Home', path: '/' },
          { name: 'Products', path: '/products' },
        ])}
      />
      <Container size="xl">
        <div className="products__layout">
          {/* sidebar */}
          <aside
            className={`products__sidebar ${filtersOpen ? 'is-open' : ''}`}
            aria-label="Product filters"
          >
            <div className="products__sidebar-head">
              <h2 className="products__sidebar-title">Filters</h2>
              <button
                type="button"
                className="products__close"
                onPointerDown={(e) => {
                  e.preventDefault()
                  setFiltersOpen(false)
                }}
                onClick={() => setFiltersOpen(false)}
                aria-label="Close filters"
              >
                <X size={18} aria-hidden="true" focusable="false" />
              </button>
            </div>

            <FilterGroup title="Category">
              <div className="products__cats">
                <label className="products__cat">
                  <input
                    type="checkbox"
                    checked={filters.categories.length === 0}
                    onChange={() => update({ categories: [] })}
                  />
                  <span>All categories</span>
                </label>
                {categoriesLoading && categories.length === 0 ? (
                  <span className="products__cat-empty">Loading…</span>
                ) : (
                  categories.map((c) => {
                    const value = c.toLowerCase()
                    const checked = filters.categories.includes(value)
                    return (
                      <label className="products__cat" key={c}>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={(e) => {
                            const set = new Set(filters.categories)
                            if (e.target.checked) set.add(value)
                            else set.delete(value)
                            update({ categories: Array.from(set) })
                          }}
                        />
                        <span>{c}</span>
                      </label>
                    )
                  })
                )}
              </div>
            </FilterGroup>

            <FilterGroup title="Price (₹)">
              <div className="products__price">
                <Input
                  type="number"
                  min={0}
                  placeholder="Min"
                  value={filters.minPrice}
                  onChange={(e) => update({ minPrice: e.target.value })}
                  aria-label="Minimum price"
                />
                <span className="products__price-dash">-</span>
                <Input
                  type="number"
                  min={0}
                  placeholder="Max"
                  value={filters.maxPrice}
                  onChange={(e) => update({ maxPrice: e.target.value })}
                  aria-label="Maximum price"
                />
              </div>
            </FilterGroup>

            <FilterGroup title="Availability">
              <Checkbox
                label="In stock only"
                checked={filters.inStock}
                onChange={(e) => update({ inStock: e.target.checked })}
              />
            </FilterGroup>

            <Button
              variant="ghost"
              fullWidth
              onClick={clearAll}
              disabled={activeChips.length === 0}
            >
              Clear all filters
            </Button>
          </aside>

          {/* main */}
          <div className="products__main">
            <div className="products__toolbar">
              <div className="products__search">
                <Input
                  type="search"
                  placeholder="Search snacks, sweets..."
                  value={searchDraft}
                  onChange={(e) => setSearchDraft(e.target.value)}
                  leftIcon={<Search size={18} />}
                  aria-label="Search products"
                />
              </div>

              <Button
                variant="secondary"
                size="md"
                leftIcon={<SlidersHorizontal size={16} />}
                onClick={() => setFiltersOpen((v) => !v)}
                className="products__filters-btn"
              >
                Filters
                {activeChips.length > 0 && (
                  <Badge variant="primary" className="products__filters-count">
                    {activeChips.length}
                  </Badge>
                )}
              </Button>

              <Select
                value={filters.sort}
                onChange={(e) => update({ sort: e.target.value })}
                options={SORT_OPTIONS}
                aria-label="Sort"
                fullWidth={false}
                className="products__sort"
              />
            </div>

            <div className="products__count">
              {isLoading
                ? 'Loading...'
                : products.length === 0
                ? 'No results'
                : `Showing ${products.length} product${products.length === 1 ? '' : 's'}`}
            </div>

            {activeChips.length > 0 && (
              <div className="products__chips" aria-label="Active filters">
                {activeChips.map((c) => (
                  <Tag key={c.key} onRemove={c.onRemove}>
                    {c.label}
                  </Tag>
                ))}
              </div>
            )}

            {isError && (
              <Alert
                variant="danger"
                title="Couldn't load products"
                action={<Button onClick={() => refetch()} size="sm">Retry</Button>}
              >
                Please check your connection and try again.
              </Alert>
            )}

            {isLoading && <ProductGridSkeleton />}

            {!isLoading && !isError && products.length === 0 && (
              <EmptyState
                icon={<ShoppingBag size={36} />}
                title="No products match your filters"
                description="Try removing a filter or searching for something else."
                action={
                  <Button onClick={clearAll} variant="secondary">
                    Clear filters
                  </Button>
                }
              />
            )}

            {!isLoading && !isError && products.length > 0 && (
              <>
                <div className="products__grid">
                  {products.map((p) => (
                    <ProductCard
                      key={p.productId || p.id}
                      product={{ ...p, productId: p.productId || p.id }}
                      onAddToCart={handleAddToCart}
                    />
                  ))}
                </div>
                {hasNextPage && (
                  <>
                    {/* Sentinel watched by IntersectionObserver to
                     * trigger auto-load of the next page. */}
                    <div
                      ref={sentinelRef}
                      className="products__sentinel"
                      aria-hidden="true"
                    />
                    <div className="products__more">
                      <Button
                        variant="secondary"
                        size="lg"
                        onClick={() => fetchNextPage()}
                        loading={isFetchingNextPage}
                      >
                        {isFetchingNextPage ? 'Loading more' : 'Load more'}
                      </Button>
                    </div>
                  </>
                )}
              </>
            )}
          </div>
        </div>
      </Container>

      {filtersOpen && (
        <div
          className="products__backdrop"
          onClick={() => setFiltersOpen(false)}
          aria-hidden="true"
        />
      )}
    </div>
  )
}

/* ---------------- helpers ---------------- */
function FilterGroup({ title, children }) {
  return (
    <div className="products__group">
      <h3 className="products__group-title">{title}</h3>
      {children}
    </div>
  )
}

function ProductGridSkeleton() {
  return (
    <div className="products__grid" aria-busy="true">
      {Array.from({ length: 8 }).map((_, i) => (
        <div className="products__skel" key={i}>
          <Skeleton width="100%" height={0} style={{ aspectRatio: '1 / 1' }} />
          <Skeleton width="80%" height={16} />
          <Skeleton width="40%" height={20} />
        </div>
      ))}
    </div>
  )
}

function buildActiveChips(filters, update) {
  const out = []
  if (filters.q)
    out.push({ key: 'q', label: `"${filters.q}"`, onRemove: () => update({ q: '' }) })
  for (const c of filters.categories) {
    out.push({
      key: `cat:${c}`,
      label: c.charAt(0).toUpperCase() + c.slice(1),
      onRemove: () =>
        update({ categories: filters.categories.filter((x) => x !== c) }),
    })
  }
  if (filters.minPrice)
    out.push({ key: 'min', label: `≥ ₹${filters.minPrice}`, onRemove: () => update({ minPrice: '' }) })
  if (filters.maxPrice)
    out.push({ key: 'max', label: `≤ ₹${filters.maxPrice}`, onRemove: () => update({ maxPrice: '' }) })
  if (filters.inStock)
    out.push({ key: 'stk', label: 'In stock', onRemove: () => update({ inStock: false }) })
  if (filters.sort) {
    const found = SORT_OPTIONS.find((s) => s.value === filters.sort)
    out.push({ key: 'sort', label: found?.label || filters.sort, onRemove: () => update({ sort: '' }) })
  }
  return out
}
