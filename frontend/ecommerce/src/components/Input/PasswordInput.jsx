/* ------------------------------------------------------------------
 * PasswordInput - Input + show/hide toggle.
 * Same API as Input.
 * ------------------------------------------------------------------ */
import { forwardRef, useId, useState } from 'react'
import clsx from 'clsx'
import './Input.css'

const PasswordInput = forwardRef(function PasswordInput(
  { label, hint, error, id, className, fullWidth = true, disabled, ...rest },
  ref,
) {
  const [show, setShow] = useState(false)
  const autoId = useId()
  const inputId = id || `pwd-${autoId}`
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
        <label htmlFor={inputId} className="ui-field__label">
          {label}
        </label>
      )}
      <div className="ui-field__control">
        <input
          ref={ref}
          id={inputId}
          type={show ? 'text' : 'password'}
          disabled={disabled}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy.join(' ') || undefined}
          className="ui-field__input"
          {...rest}
        />
        <button
          type="button"
          className="ui-field__toggle"
          onClick={() => setShow((s) => !s)}
          aria-label={show ? 'Hide password' : 'Show password'}
          tabIndex={-1}
        >
          {show ? 'Hide' : 'Show'}
        </button>
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

export default PasswordInput
