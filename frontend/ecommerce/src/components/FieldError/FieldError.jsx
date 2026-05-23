/* ------------------------------------------------------------------
 * FieldError - inline error text. Useful for non-input contexts where
 * you can't pass `error` to the field component directly (e.g. an
 * entire form-section error, or grouping radio buttons).
 * ------------------------------------------------------------------ */
import clsx from 'clsx'

export default function FieldError({ children, className, id }) {
  if (!children) return null
  return (
    <p
      id={id}
      role="alert"
      className={clsx('ui-field__error', className)}
      style={{ marginTop: 'var(--space-1)' }}
    >
      {children}
    </p>
  )
}
