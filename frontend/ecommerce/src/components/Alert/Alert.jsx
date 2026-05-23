/* ------------------------------------------------------------------
 * Alert - inline page-level message.
 * Variants: info | success | warning | danger
 *
 * Usage:
 *   <Alert variant="danger" title="Login failed">
 *     Invalid email or password.
 *   </Alert>
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Alert.css'

export default function Alert({
  variant = 'info',
  title,
  icon,
  onClose,
  className,
  children,
  ...rest
}) {
  return (
    <div
      role="alert"
      className={clsx('ui-alert', `ui-alert--${variant}`, className)}
      {...rest}
    >
      {icon && <div className="ui-alert__icon">{icon}</div>}
      <div className="ui-alert__body">
        {title && <div className="ui-alert__title">{title}</div>}
        {children && <div className="ui-alert__msg">{children}</div>}
      </div>
      {onClose && (
        <button
          type="button"
          className="ui-alert__close"
          aria-label="Dismiss"
          onClick={onClose}
        >
          x
        </button>
      )}
    </div>
  )
}
