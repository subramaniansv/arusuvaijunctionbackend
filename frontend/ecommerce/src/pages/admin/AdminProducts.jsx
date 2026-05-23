/**
 * Admin products page.
 *
 * Lists every product (paginated by the admin endpoint) and lets
 * the operator edit stock inline. The "Add product" action lives
 * in the page header and routes to the create-product form.
 *
 * Stock edit flow:
 *   1. Click pencil -> row swaps name+input into edit mode.
 *   2. Save -> PUT /api/product with the full product (current values
 *      preserved, stockQuantity overwritten).
 */
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Plus, Pencil, Check, X } from 'lucide-react'
import toast from 'react-hot-toast'
import {
  useAdminProducts,
  useUpdateProduct,
} from '../../lib/admin'
import {
  Alert,
  Badge,
  Button,
  IconButton,
  PriceTag,
  Skeleton,
  EmptyState,
} from '../../components'

function StockCell({ product }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(String(product.stockQuantity ?? 0))
  const mut = useUpdateProduct()

  const stock = product.stockQuantity ?? 0
  const variant =
    stock <= 0 ? 'danger' : stock <= 5 ? 'warning' : 'success'

  if (!editing) {
    return (
      <div className="admin-stock">
        <Badge variant={variant}>{stock} in stock</Badge>
        <IconButton
          size="sm"
          variant="ghost"
          aria-label="Edit stock"
          onClick={() => {
            setValue(String(stock))
            setEditing(true)
          }}
        >
          <Pencil size={14} />
        </IconButton>
      </div>
    )
  }

  const save = async () => {
    const next = Number(value)
    if (!Number.isFinite(next) || next < 0) {
      toast.error('Stock must be a non-negative number')
      return
    }
    try {
      await mut.mutateAsync({ ...product, stockQuantity: next })
      toast.success('Stock updated')
      setEditing(false)
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not update stock')
    }
  }

  return (
    <div className="admin-stock admin-stock--edit">
      <input
        type="number"
        min="0"
        className="admin-stock__input"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={mut.isPending}
        autoFocus
      />
      <IconButton
        size="sm"
        variant="solid"
        aria-label="Save stock"
        onClick={save}
        disabled={mut.isPending}
      >
        <Check size={14} />
      </IconButton>
      <IconButton
        size="sm"
        variant="ghost"
        aria-label="Cancel"
        onClick={() => setEditing(false)}
        disabled={mut.isPending}
      >
        <X size={14} />
      </IconButton>
    </div>
  )
}

export default function AdminProducts() {
  const { data: products = [], isLoading, error } = useAdminProducts({ limit: 100 })
  const nav = useNavigate()

  // Clicks bubble up from the row. We only treat a click as "open the
  // edit page" if it landed on the row chrome itself - not on the
  // stock-edit controls or any other interactive element nested
  // inside, which are stopPropagation-friendly natively.
  const openEdit = (id, e) => {
    // Bail out if the user clicked an input, button or anything with
    // its own click handler. The stock-edit cell stops propagation
    // explicitly below.
    const tag = e.target.tagName
    if (tag === 'INPUT' || tag === 'BUTTON' || tag === 'SVG' || tag === 'PATH') {
      return
    }
    nav(`/admin/products/${id}`)
  }

  return (
    <div className="stack">
      <div className="admin-section__head">
        <div>
          <h2 className="admin-section__title">Products</h2>
          <p className="admin-section__hint">
            {products.length} item{products.length === 1 ? '' : 's'} in catalog.
          </p>
        </div>
        <Button as={Link} to="/admin/products/new" variant="primary">
          <Plus size={16} /> Add product
        </Button>
      </div>

      {error && <Alert variant="danger">Could not load products.</Alert>}

      {isLoading ? (
        <div className="stack">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} height="64px" />
          ))}
        </div>
      ) : products.length === 0 ? (
        <EmptyState
          title="No products yet"
          description="Add your first product to populate the catalog."
        />
      ) : (
        <div className="admin-table">
          <div className="admin-table__row admin-table__row--head">
            <span>Product</span>
            <span>Category</span>
            <span>Price</span>
            <span>Stock</span>
            <span>Status</span>
          </div>
          {products.map((p) => (
            <div
              key={p.id}
              className="admin-table__row admin-table__row--clickable"
              onClick={(e) => openEdit(p.id, e)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  nav(`/admin/products/${p.id}`)
                }
              }}
            >
              <div className="admin-product-cell">
                {p.primaryImageUrl ? (
                  <img
                    src={p.primaryImageUrl}
                    alt=""
                    className="admin-product-cell__img"
                  />
                ) : (
                  <div className="admin-product-cell__img admin-product-cell__img--ph" />
                )}
                <div>
                  <div className="admin-product-cell__name">{p.name}</div>
                  <div className="admin-product-cell__id">
                    {String(p.id).slice(0, 8)}…
                  </div>
                </div>
              </div>
              <span className="text-muted">{p.category || '—'}</span>
              <PriceTag amount={Number(p.price) || 0} />
              <div onClick={(e) => e.stopPropagation()}>
                <StockCell product={p} />
              </div>
              <Badge variant={p.active ? 'success' : 'neutral'}>
                {p.active ? 'Active' : 'Inactive'}
              </Badge>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
