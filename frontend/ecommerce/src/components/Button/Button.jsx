/* ------------------------------------------------------------------
 * Button
 * Variants:  primary | secondary | ghost | danger
 * Sizes:     sm | md | lg
 * Props:     loading, fullWidth, leftIcon, rightIcon, as (default 'button')
 *
 * Usage:
 *   <Button>Buy now</Button>
 *   <Button variant="secondary" size="sm" leftIcon={<Plus/>}>Add</Button>
 *   <Button as={Link} to="/cart" variant="ghost">Cart</Button>
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import Spinner from '../Spinner/Spinner.jsx'
import './Button.css'

export default function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  leftIcon,
  rightIcon,
  disabled,
  className,
  children,
  as: Comp = 'button',
  type,
  ...rest
}) {
  const isButton = Comp === 'button'
  return (
    <Comp
      {...(isButton ? { type: type || 'button', disabled: disabled || loading } : {})}
      className={clsx(
        'ui-btn',
        `ui-btn--${variant}`,
        `ui-btn--${size}`,
        fullWidth && 'ui-btn--full',
        loading && 'is-loading',
        className,
      )}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <Spinner size="sm" className="ui-btn__spinner" />}
      {!loading && leftIcon && <span className="ui-btn__icon">{leftIcon}</span>}
      <span className="ui-btn__label">{children}</span>
      {!loading && rightIcon && <span className="ui-btn__icon">{rightIcon}</span>}
    </Comp>
  )
}
