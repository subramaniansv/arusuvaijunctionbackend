/* ------------------------------------------------------------------
 * Textarea - multi-line text input, same field API as Input.
 * ------------------------------------------------------------------ */
import { forwardRef, useId } from 'react'
import clsx from 'clsx'
import '../Input/Input.css'

const Textarea = forwardRef(function Textarea(
  { label, hint, error, id, rows = 4, className, fullWidth = true, disabled, ...rest },
  ref,
) {
  const autoId = useId()
  const inputId = id || `txa-${autoId}`
  const describedBy = []
  if (hint) describedBy.push(`${inputId}-hint`)
  if (error) describedBy.push(`${inputId}-err`)

  return (
    <div
      className={clsx(
        'ui-field',
        fullWidth && 'ui-field--full',
        error && 'is-invalid',
        disabled && 'is-disabled',
        className,
      )}
    >
      {label && (
        <label htmlFor={inputId} className="ui-field__label">{label}</label>
      )}
      <div className="ui-field__control">
        <textarea
          ref={ref}
          id={inputId}
          rows={rows}
          disabled={disabled}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy.join(' ') || undefined}
          className="ui-field__textarea"
          {...rest}
        />
      </div>
      {hint && !error && (
        <p id={`${inputId}-hint`} className="ui-field__hint">{hint}</p>
      )}
      {error && (
        <p id={`${inputId}-err`} className="ui-field__error" role="alert">{error}</p>
      )}
    </div>
  )
})

export default Textarea
