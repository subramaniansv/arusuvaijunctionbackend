/* ------------------------------------------------------------------
 * Tag / Chip - small removable label, often used for active filters.
 * Use `onRemove` to render an X button.
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Tag.css'

export default function Tag({ children, onRemove, className, ...rest }) {
  return (
    <span className={clsx('ui-tag', className)} {...rest}>
      <span className="ui-tag__label">{children}</span>
      {onRemove && (
        <button
          type="button"
          className="ui-tag__remove"
          aria-label="Remove"
          onClick={onRemove}
        >
          ×
        </button>
      )}
    </span>
  )
}
