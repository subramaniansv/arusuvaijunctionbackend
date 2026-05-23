/* ------------------------------------------------------------------
 * Input - styled text input with label, hint, error, optional icons.
 * Forwards ref so it plays nicely with react-hook-form's register().
 *
 * Props:
 *   label, hint, error, leftIcon, rightIcon, id, fullWidth, ...inputProps
 *
 * Usage with react-hook-form:
 *   <Input label="Email" type="email" error={errors.email?.message}
 *          {...register('email')} />
 * ------------------------------------------------------------------ */
import { forwardRef, useId } from 'react'
import clsx from 'clsx'
import './Input.css'

const Input = forwardRef(function Input(
  {
    label,
    hint,
    error,
    leftIcon,
    rightIcon,
    id,
    className,
    fullWidth = true,
    type = 'text',
    disabled,
    ...rest
  },
  ref,
) {
  const autoId = useId()
  const inputId = id || `inp-${autoId}`
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
        {leftIcon && <span className="ui-field__icon ui-field__icon--left">{leftIcon}</span>}
        <input
          ref={ref}
          id={inputId}
          type={type}
          disabled={disabled}
          aria-invalid={!!error || undefined}
          aria-describedby={describedBy.join(' ') || undefined}
          className="ui-field__input"
          {...rest}
        />
        {rightIcon && <span className="ui-field__icon ui-field__icon--right">{rightIcon}</span>}
      </div>

      {hint && !error && (
        <p id={`${inputId}-hint`} className="ui-field__hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${inputId}-err`} className="ui-field__error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
})

export default Input
