/**
 * Admin: edit an existing product.
 *
 * One page covers everything the admin would want to change:
 *   - core fields  (name, description, category, ingredients,
 *                   price, stockQuantity, active)        -> PUT /api/product
 *   - images       (add new / delete existing / set primary)
 *                                                         -> /api/product/image
 *   - variants     (list, create, update, delete)
 *                                                         -> /api/product/variant
 *   - destroy      (DELETE /api/product)
 *
 * The page loads the product via GET /api/product?productId=… which
 * already returns the images + variants in a single round-trip; the
 * variants list is refreshed separately so admins can see inactive
 * (soft-deleted) entries via the toggle.
 */
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import {
  ArrowLeft,
  Check,
  ImagePlus,
  Plus,
  Star,
  Trash2,
} from 'lucide-react'
import toast from 'react-hot-toast'

import {
  useAddProductImage,
  useAdminProduct,
  useCreateVariant,
  useDeleteProduct,
  useDeleteProductImage,
  useDeleteVariant,
  useProductVariants,
  useSetPrimaryImage,
  useUpdateProduct,
  useUpdateVariant,
} from '../../lib/admin'
import { useCategories } from '../../lib/products'
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  IconButton,
  Input,
  Select,
  Skeleton,
  Textarea,
} from '../../components'

const schema = z.object({
  name: z.string().min(2, 'Name is required'),
  description: z.string().min(5, 'Description is required'),
  category: z.string().min(1, 'Pick or type a category'),
  ingredients: z.string().optional(),
  price: z.coerce.number().positive('Price must be > 0'),
  stockQuantity: z.coerce.number().int().min(0, 'Stock cannot be negative'),
  active: z.boolean().optional(),
})

export default function AdminProductEdit() {
  const { id: productId } = useParams()
  const nav = useNavigate()
  const { data: product, isLoading, error } = useAdminProduct(productId)
  const { data: categories = [] } = useCategories()

  const update = useUpdateProduct()
  const destroy = useDeleteProduct()
  const addImage = useAddProductImage()
  const deleteImage = useDeleteProductImage()
  const setPrimary = useSetPrimaryImage()

  const [includeInactiveVariants, setIncludeInactiveVariants] = useState(false)
  const { data: variants = [] } = useProductVariants(productId, {
    includeInactive: includeInactiveVariants,
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      description: '',
      category: '',
      ingredients: '',
      price: 0,
      stockQuantity: 0,
      active: true,
    },
  })

  // Hydrate the form once the product arrives.
  useEffect(() => {
    if (!product) return
    reset({
      name: product.name || '',
      description: product.description || '',
      category: product.category || '',
      ingredients: product.ingredients || '',
      price: product.price ?? 0,
      stockQuantity: product.stockQuantity ?? 0,
      active: !!product.active,
    })
  }, [product, reset])

  const images = product?.images || []

  const onSubmit = async (values) => {
    if (!product) return
    try {
      await update.mutateAsync({
        ...product,
        name: values.name.trim(),
        description: values.description.trim(),
        category: values.category.trim(),
        ingredients: values.ingredients?.trim() || '',
        price: Number(values.price),
        stockQuantity: Number(values.stockQuantity),
        active: !!values.active,
      })
      toast.success('Product updated')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not update product')
    }
  }

  const onPickImages = async (e) => {
    const incoming = Array.from(e.target.files || [])
    e.target.value = ''
    for (const file of incoming) {
      try {
        await addImage.mutateAsync({ productId, file })
      } catch (err) {
        toast.error(
          `Upload failed for ${file.name}: ${
            err?.response?.data?.message || 'unknown error'
          }`,
        )
      }
    }
    if (incoming.length) toast.success('Image(s) uploaded')
  }

  const onDeleteImage = async (img) => {
    if (!confirm('Delete this image?')) return
    try {
      await deleteImage.mutateAsync({ imageId: img.id, productId })
      toast.success('Image removed')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not delete image')
    }
  }

  const onSetPrimary = async (img) => {
    if (img.primary) return
    try {
      await setPrimary.mutateAsync({ productId, imageId: img.id })
      toast.success('Primary image updated')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not set primary')
    }
  }

  const onDestroy = async () => {
    if (!product) return
    if (
      !confirm(
        `Permanently delete "${product.name}"? This also removes all its images.`,
      )
    )
      return
    try {
      await destroy.mutateAsync(productId)
      toast.success('Product deleted')
      nav('/admin/products')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not delete product')
    }
  }

  if (isLoading) {
    return (
      <div className="stack">
        <Skeleton height="32px" />
        <Skeleton height="160px" />
        <Skeleton height="160px" />
      </div>
    )
  }
  if (error || !product) {
    return (
      <Alert variant="danger">
        Could not load product. It may have been deleted.
      </Alert>
    )
  }

  return (
    <div className="stack">
      <div className="admin-section__head">
        <div className="admin-section__head-left">
          <IconButton
            variant="ghost"
            aria-label="Back to products"
            onClick={() => nav('/admin/products')}
          >
            <ArrowLeft size={18} />
          </IconButton>
          <div>
            <h2 className="admin-section__title">{product.name}</h2>
            <p className="admin-section__hint">
              {String(product.id).slice(0, 8)}…
            </p>
          </div>
        </div>
        <Button
          variant="ghost"
          onClick={onDestroy}
          loading={destroy.isPending}
        >
          <Trash2 size={16} /> Delete product
        </Button>
      </div>

      {/* ----- core fields ----- */}
      <form className="admin-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="admin-form__grid">
          <Input
            label="Name"
            {...register('name')}
            error={errors.name?.message}
          />
          <Select
            label="Category"
            {...register('category')}
            error={errors.category?.message}
          >
            <option value="">Select a category…</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
          <Input
            label="Price (₹)"
            type="number"
            step="0.01"
            min="0"
            {...register('price')}
            error={errors.price?.message}
          />
          <Input
            label="Stock quantity"
            type="number"
            min="0"
            {...register('stockQuantity')}
            error={errors.stockQuantity?.message}
          />
        </div>

        <Textarea
          label="Description"
          rows={4}
          {...register('description')}
          error={errors.description?.message}
        />
        <Textarea
          label="Ingredients (optional)"
          rows={2}
          {...register('ingredients')}
          error={errors.ingredients?.message}
        />

        <Checkbox label="Active (visible to customers)" {...register('active')} />

        {update.isError && (
          <Alert variant="danger">
            {update.error?.response?.data?.message ||
              'Could not save changes.'}
          </Alert>
        )}

        <div className="admin-form__actions">
          <Button
            type="button"
            variant="ghost"
            onClick={() => nav('/admin/products')}
          >
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            loading={update.isPending}
            disabled={!isDirty}
          >
            Save changes
          </Button>
        </div>
      </form>

      {/* ----- images ----- */}
      <section className="admin-form">
        <div className="admin-form__images-head">
          <span className="ui-field__label">Images</span>
          <span className="text-muted">
            Click the star to promote an image to primary. The primary
            image is what shoppers see in catalog cards.
          </span>
        </div>

        <label className="admin-image-picker">
          <input
            type="file"
            accept="image/*"
            multiple
            onChange={onPickImages}
            hidden
            disabled={addImage.isPending}
          />
          <ImagePlus size={18} />
          <span>{addImage.isPending ? 'Uploading…' : 'Add images…'}</span>
        </label>

        {images.length === 0 ? (
          <p className="text-muted">No images yet.</p>
        ) : (
          <ul className="admin-image-list">
            {images.map((img) => (
              <li key={img.id} className="admin-image-list__item">
                <img
                  src={img.imageUrl}
                  alt=""
                  loading="lazy"
                  decoding="async"
                  className="admin-image-list__thumb"
                />
                <span className="admin-image-list__name">
                  {String(img.objectKey).split('/').pop()}
                </span>
                {img.primary && (
                  <span className="admin-image-list__primary">Primary</span>
                )}
                {!img.primary && (
                  <IconButton
                    variant="ghost"
                    size="sm"
                    aria-label="Make primary"
                    onClick={() => onSetPrimary(img)}
                    disabled={setPrimary.isPending}
                  >
                    <Star size={14} />
                  </IconButton>
                )}
                <IconButton
                  variant="ghost"
                  size="sm"
                  aria-label="Delete image"
                  onClick={() => onDeleteImage(img)}
                  disabled={deleteImage.isPending}
                >
                  <Trash2 size={14} />
                </IconButton>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* ----- variants ----- */}
      <VariantSection
        productId={productId}
        variants={variants}
        includeInactive={includeInactiveVariants}
        onToggleInactive={() =>
          setIncludeInactiveVariants((v) => !v)
        }
      />
    </div>
  )
}

/* ---------------------------------------------------------------- */
/* Variant management. Each row is an inline editor that toggles    */
/* between "view" and "edit" modes. A bottom row lets the admin     */
/* draft a new variant. We keep this in the same file because it    */
/* shares the page's data flow and styles.                          */
/* ---------------------------------------------------------------- */

function VariantSection({
  productId,
  variants,
  includeInactive,
  onToggleInactive,
}) {
  const createVariant = useCreateVariant()
  const updateVariant = useUpdateVariant()
  const deleteVariant = useDeleteVariant()

  // Local draft for "add variant" row. Pre-suggest the next sortOrder.
  const nextSort = useMemo(
    () =>
      variants.length
        ? Math.max(...variants.map((v) => v.sortOrder ?? 0)) + 1
        : 0,
    [variants],
  )
  const [draft, setDraft] = useState({
    label: '',
    price: '',
    stockQuantity: 0,
    sortOrder: nextSort,
  })
  useEffect(() => {
    setDraft((d) => ({ ...d, sortOrder: nextSort }))
  }, [nextSort])

  const saveDraft = async () => {
    if (!draft.label.trim()) {
      toast.error('Variant label is required')
      return
    }
    const price = Number(draft.price)
    if (!Number.isFinite(price) || price <= 0) {
      toast.error('Variant price must be > 0')
      return
    }
    try {
      await createVariant.mutateAsync({
        productId,
        label: draft.label.trim(),
        price,
        stockQuantity: Number(draft.stockQuantity) || 0,
        sortOrder: Number(draft.sortOrder) || 0,
      })
      toast.success('Variant added')
      setDraft({
        label: '',
        price: '',
        stockQuantity: 0,
        sortOrder: nextSort + 1,
      })
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not add variant')
    }
  }

  return (
    <section className="admin-form">
      <div className="admin-form__images-head">
        <span className="ui-field__label">Variants</span>
        <span className="text-muted">
          Sizes or pack counts with their own price and stock.
        </span>
      </div>

      <label className="admin-stock admin-stock--edit">
        <input
          type="checkbox"
          checked={includeInactive}
          onChange={onToggleInactive}
        />
        <span className="text-muted">Show inactive (soft-deleted) variants</span>
      </label>

      {variants.length === 0 ? (
        <p className="text-muted">No variants yet.</p>
      ) : (
        <ul className="admin-variant-list">
          {variants.map((v) => (
            <VariantRow
              key={v.variantId}
              productId={productId}
              variant={v}
              onSave={(patch) =>
                updateVariant.mutateAsync({
                  variantId: v.variantId,
                  productId,
                  ...patch,
                })
              }
              onDelete={() => deleteVariant.mutateAsync({
                variantId: v.variantId,
                productId,
              })}
            />
          ))}
        </ul>
      )}

      <div className="admin-variant-list">
        <div className="admin-variant-list__item">
          <Input
            label="Label"
            placeholder="e.g. 500g"
            value={draft.label}
            onChange={(e) =>
              setDraft((d) => ({ ...d, label: e.target.value }))
            }
          />
          <Input
            label="Price (₹)"
            type="number"
            step="0.01"
            min="0"
            value={draft.price}
            onChange={(e) =>
              setDraft((d) => ({ ...d, price: e.target.value }))
            }
          />
          <Input
            label="Stock"
            type="number"
            min="0"
            value={draft.stockQuantity}
            onChange={(e) =>
              setDraft((d) => ({ ...d, stockQuantity: e.target.value }))
            }
          />
          <Button
            type="button"
            variant="primary"
            onClick={saveDraft}
            loading={createVariant.isPending}
          >
            <Plus size={14} /> Add
          </Button>
        </div>
      </div>
    </section>
  )
}

function VariantRow({ productId: _pid, variant, onSave, onDelete }) {
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState({
    label: variant.label,
    price: variant.price,
    stockQuantity: variant.stockQuantity,
    sortOrder: variant.sortOrder,
    active: variant.active,
  })
  useEffect(() => {
    setForm({
      label: variant.label,
      price: variant.price,
      stockQuantity: variant.stockQuantity,
      sortOrder: variant.sortOrder,
      active: variant.active,
    })
  }, [variant])

  const save = async () => {
    if (!form.label?.trim()) {
      toast.error('Label is required')
      return
    }
    const price = Number(form.price)
    if (!Number.isFinite(price) || price <= 0) {
      toast.error('Price must be > 0')
      return
    }
    try {
      await onSave({
        label: form.label.trim(),
        price,
        stockQuantity: Number(form.stockQuantity) || 0,
        sortOrder: Number(form.sortOrder) || 0,
        isActive: !!form.active,
      })
      toast.success('Variant saved')
      setEditing(false)
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not save variant')
    }
  }

  const remove = async () => {
    if (!confirm(`Delete variant "${variant.label}"?`)) return
    try {
      await onDelete()
      toast.success('Variant deleted')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not delete variant')
    }
  }

  if (!editing) {
    return (
      <li className="admin-variant-list__item">
        <div>
          <div className="admin-product-cell__name">{variant.label}</div>
          <div className="admin-product-cell__id">
            sort #{variant.sortOrder}
          </div>
        </div>
        <div>₹{Number(variant.price).toFixed(2)}</div>
        <div>
          <Badge
            variant={
              variant.stockQuantity <= 0
                ? 'danger'
                : variant.stockQuantity <= 5
                  ? 'warning'
                  : 'success'
            }
          >
            {variant.stockQuantity} in stock
          </Badge>
        </div>
        <Badge variant={variant.active ? 'success' : 'neutral'}>
          {variant.active ? 'Active' : 'Inactive'}
        </Badge>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => setEditing(true)}
        >
          Edit
        </Button>
        <IconButton
          variant="ghost"
          size="sm"
          aria-label={`Delete ${variant.label}`}
          onClick={remove}
        >
          <Trash2 size={14} />
        </IconButton>
      </li>
    )
  }

  return (
    <li className="admin-variant-list__item">
      <Input
        label="Label"
        value={form.label}
        onChange={(e) => setForm((f) => ({ ...f, label: e.target.value }))}
      />
      <Input
        label="Price (₹)"
        type="number"
        step="0.01"
        min="0"
        value={form.price}
        onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
      />
      <Input
        label="Stock"
        type="number"
        min="0"
        value={form.stockQuantity}
        onChange={(e) =>
          setForm((f) => ({ ...f, stockQuantity: e.target.value }))
        }
      />
      <Checkbox
        label="Active"
        checked={!!form.active}
        onChange={(e) =>
          setForm((f) => ({ ...f, active: e.target.checked }))
        }
      />
      <IconButton variant="solid" size="sm" aria-label="Save" onClick={save}>
        <Check size={14} />
      </IconButton>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => setEditing(false)}
      >
        Cancel
      </Button>
    </li>
  )
}
