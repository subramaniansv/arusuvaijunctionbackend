/* ------------------------------------------------------------------
 * Checkbox - styled native checkbox with adjacent label.
 * ------------------------------------------------------------------ */
import { forwardRef, useId } from 'react'
import clsx from 'clsx'
import './Checkbox.css'

const Checkbox = forwardRef(function Checkbox(
  { label, error, id, className, disabled, ...rest },
  ref,
) {
  const autoId = useId()
  const inputId = id || `chk-${autoId}`
  return (
    <label
      htmlFor={inputId}
      className={clsx(
        'ui-checkbox',
        disabled && 'is-disabled',
        error && 'is-invalid',
        className,
      )}
    >
      <input
        ref={ref}
        id={inputId}
        type="checkbox"
        disabled={disabled}
        aria-invalid={!!error || undefined}
        className="ui-checkbox__input"
        {...rest}
      />
      <span className="ui-checkbox__box" aria-hidden="true">
        <svg viewBox="0 0 16 16" fill="none">
          <path
            d="M3 8.5L6.5 12L13 4.5"
            stroke="currentColor"
            strokeWidth="2.25"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      {label && <span className="ui-checkbox__label">{label}</span>}
    </label>
  )
})

export default Checkbox
