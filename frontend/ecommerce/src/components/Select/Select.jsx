/* ------------------------------------------------------------------
 * Select - native <select> styled to match Input.
 * Pass options as either children <option> or `options=[{value,label}]`.
 * ------------------------------------------------------------------ */
import { forwardRef, useId } from 'react'
import clsx from 'clsx'
import '../Input/Input.css'

const Select = forwardRef(function Select(
  {
    label,
    hint,
    error,
    id,
    options,
    placeholder,
    className,
    fullWidth = true,
    disabled,
    children,
    ...rest
  },
  ref,
) {
  const autoId = useId()
  const inputId = id || `sel-${autoId}`
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
        <select
          ref={ref}
          id={inputId}
          disabled={disabled}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy.join(' ') || undefined}
          className="ui-field__select"
          {...rest}
        >
          {placeholder && <option value="">{placeholder}</option>}
          {options
            ? options.map((opt) => (
                <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                  {opt.label}
                </option>
              ))
            : children}
        </select>
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

export default Select
