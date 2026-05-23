/* ------------------------------------------------------------------
 * QuantityStepper - [-] N [+] control for cart line items.
 *
 * Controlled:
 *   <QuantityStepper value={qty} onChange={setQty} min={1} max={99} />
 *
 * Calls onChange with the new value; clamps to [min, max].
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './QuantityStepper.css'

export default function QuantityStepper({
  value = 1,
  onChange,
  min = 1,
  max = 99,
  size = 'md',
  disabled = false,
  className,
  ...rest
}) {
  const clamp = (n) => Math.max(min, Math.min(max, n))
  const dec = () => !disabled && onChange?.(clamp(value - 1))
  const inc = () => !disabled && onChange?.(clamp(value + 1))
  const onInput = (e) => {
    const n = parseInt(e.target.value, 10)
    if (!isNaN(n)) onChange?.(clamp(n))
  }

  return (
    <div
      className={clsx('ui-qty', `ui-qty--${size}`, disabled && 'is-disabled', className)}
      {...rest}
    >
      <button
        type="button"
        className="ui-qty__btn"
        onClick={dec}
        disabled={disabled || value <= min}
        aria-label="Decrease quantity"
      >
        −
      </button>
      <input
        type="number"
        className="ui-qty__value"
        value={value}
        onChange={onInput}
        min={min}
        max={max}
        disabled={disabled}
        aria-label="Quantity"
      />
      <button
        type="button"
        className="ui-qty__btn"
        onClick={inc}
        disabled={disabled || value >= max}
        aria-label="Increase quantity"
      >
        +
      </button>
    </div>
  )
}
