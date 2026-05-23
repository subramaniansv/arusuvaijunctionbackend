/**
 * Admin: create a new product.
 *
 * Posts multipart/form-data to /api/product:
 *   product : JSON.stringify({ name, description, category, ingredients, price, stockQuantity })
 *   images  : 0..N file parts (first is treated as primary by the backend)
 *
 * Validation is local (zod). The backend forces `active=true` so we
 * don't expose that toggle here.
 */
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowLeft, ImagePlus, Plus, Trash2 } from 'lucide-react'
import toast from 'react-hot-toast'

import { useCreateProduct, useCreateVariant } from '../../lib/admin'
import { useCategories } from '../../lib/products'
import {
  Alert,
  Button,
  IconButton,
  Input,
  Textarea,
  Select,
} from '../../components'

const schema = z.object({
  name: z.string().min(2, 'Name is required'),
  description: z.string().min(5, 'Description is required'),
  category: z.string().min(1, 'Pick or type a category'),
  ingredients: z.string().optional(),
  price: z.coerce.number().positive('Price must be > 0'),
  stockQuantity: z.coerce.number().int().min(0, 'Stock cannot be negative'),
})

export default function AdminProductNew() {
  const nav = useNavigate()
  const { data: categories = [] } = useCategories()
  const create = useCreateProduct()
  const createVariant = useCreateVariant()
  const [files, setFiles] = useState([])
  // Draft variants are kept in memory until the product is created.
  // The backend insists on a productId for every variant row, so we
  // can't POST them until the parent product has an id.
  const [variants, setVariants] = useState([])

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: {
      name: '',
      description: '',
      category: '',
      ingredients: '',
      price: '',
      stockQuantity: 0,
    },
  })

  const onPick = (e) => {
    const incoming = Array.from(e.target.files || [])
    setFiles((prev) => [...prev, ...incoming])
    e.target.value = '' // allow re-picking same file
  }

  const removeFile = (idx) =>
    setFiles((prev) => prev.filter((_, i) => i !== idx))

  // ---- variants (draft) ----
  const addVariantRow = () =>
    setVariants((prev) => [
      ...prev,
      { label: '', price: '', stockQuantity: 0, sortOrder: prev.length },
    ])
  const updateVariantRow = (idx, patch) =>
    setVariants((prev) =>
      prev.map((v, i) => (i === idx ? { ...v, ...patch } : v)),
    )
  const removeVariantRow = (idx) =>
    setVariants((prev) => prev.filter((_, i) => i !== idx))

  const validateVariants = () => {
    for (const [i, v] of variants.entries()) {
      if (!v.label || !String(v.label).trim()) {
        return `Variant #${i + 1}: label is required`
      }
      const price = Number(v.price)
      if (!Number.isFinite(price) || price <= 0) {
        return `Variant #${i + 1}: price must be > 0`
      }
      const stock = Number(v.stockQuantity)
      if (!Number.isFinite(stock) || stock < 0) {
        return `Variant #${i + 1}: stock must be ≥ 0`
      }
    }
    return null
  }

  const onSubmit = async (values) => {
    const variantErr = validateVariants()
    if (variantErr) {
      toast.error(variantErr)
      return
    }
    try {
      const created = await create.mutateAsync({
        product: {
          name: values.name.trim(),
          description: values.description.trim(),
          category: values.category.trim(),
          ingredients: values.ingredients?.trim() || '',
          price: Number(values.price),
          stockQuantity: Number(values.stockQuantity),
        },
        images: files,
      })
      // Best-effort: push each draft variant. Failures here don't roll
      // back the product — the admin can finish on the edit page.
      const productId = created?.id
      if (productId && variants.length > 0) {
        for (const [i, v] of variants.entries()) {
          try {
            await createVariant.mutateAsync({
              productId,
              label: String(v.label).trim(),
              price: Number(v.price),
              stockQuantity: Number(v.stockQuantity) || 0,
              sortOrder: Number(v.sortOrder) || i,
            })
          } catch (e) {
            toast.error(
              `Variant "${v.label}" failed: ${
                e?.response?.data?.message || 'unknown error'
              }`,
            )
          }
        }
      }
      toast.success('Product created')
      // Send the admin to the edit page so they can fine-tune images,
      // variants and inactivate-flags without an extra click.
      if (productId) {
        nav(`/admin/products/${productId}`)
      } else {
        nav('/admin/products')
      }
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not create product')
    }
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
          <h2 className="admin-section__title">Add product</h2>
        </div>
      </div>

      <form className="admin-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="admin-form__grid">
          <Input
            label="Name"
            placeholder="e.g. Filter Coffee Powder"
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
          placeholder="What is it, who is it for…"
          {...register('description')}
          error={errors.description?.message}
        />

        <Textarea
          label="Ingredients (optional)"
          rows={2}
          placeholder="Comma-separated list"
          {...register('ingredients')}
          error={errors.ingredients?.message}
        />

        <div className="admin-form__images">
          <div className="admin-form__images-head">
            <span className="ui-field__label">Product images</span>
            <span className="text-muted">First image is used as the primary.</span>
          </div>

          <label className="admin-image-picker">
            <input
              type="file"
              accept="image/*"
              multiple
              onChange={onPick}
              hidden
            />
            <ImagePlus size={18} />
            <span>Add images…</span>
          </label>

          {files.length > 0 && (
            <ul className="admin-image-list">
              {files.map((f, i) => (
                <li key={i} className="admin-image-list__item">
                  <img
                    src={URL.createObjectURL(f)}
                    alt=""
                    className="admin-image-list__thumb"
                  />
                  <span className="admin-image-list__name">{f.name}</span>
                  {i === 0 && (
                    <span className="admin-image-list__primary">Primary</span>
                  )}
                  <IconButton
                    variant="ghost"
                    size="sm"
                    aria-label={`Remove ${f.name}`}
                    onClick={() => removeFile(i)}
                  >
                    <Trash2 size={14} />
                  </IconButton>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="admin-form__images">
          <div className="admin-form__images-head">
            <span className="ui-field__label">Variants (optional)</span>
            <span className="text-muted">
              Sizes / pack counts. Each variant has its own price and stock.
            </span>
          </div>

          {variants.length > 0 && (
            <ul className="admin-variant-list">
              {variants.map((v, i) => (
                <li key={i} className="admin-variant-list__item">
                  <Input
                    label="Label"
                    placeholder="e.g. 250g"
                    value={v.label}
                    onChange={(e) =>
                      updateVariantRow(i, { label: e.target.value })
                    }
                  />
                  <Input
                    label="Price (₹)"
                    type="number"
                    step="0.01"
                    min="0"
                    value={v.price}
                    onChange={(e) =>
                      updateVariantRow(i, { price: e.target.value })
                    }
                  />
                  <Input
                    label="Stock"
                    type="number"
                    min="0"
                    value={v.stockQuantity}
                    onChange={(e) =>
                      updateVariantRow(i, { stockQuantity: e.target.value })
                    }
                  />
                  <IconButton
                    variant="ghost"
                    size="sm"
                    aria-label={`Remove variant ${i + 1}`}
                    onClick={() => removeVariantRow(i)}
                  >
                    <Trash2 size={14} />
                  </IconButton>
                </li>
              ))}
            </ul>
          )}

          <Button
            type="button"
            variant="ghost"
            onClick={addVariantRow}
          >
            <Plus size={16} /> Add variant
          </Button>
        </div>

        {create.isError && (
          <Alert variant="danger">
            {create.error?.response?.data?.message ||
              'Something went wrong. Please retry.'}
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
          <Button type="submit" variant="primary" loading={create.isPending}>
            Create product
          </Button>
        </div>
      </form>
    </div>
  )
}
