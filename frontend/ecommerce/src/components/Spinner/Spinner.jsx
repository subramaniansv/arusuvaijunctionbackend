/* ------------------------------------------------------------------
 * Spinner - inline loading indicator.
 * Sizes: sm | md | lg
 * Use `inline` to flow with text. Defaults to a centered block.
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Spinner.css'

export default function Spinner({ size = 'md', className, label = 'Loading', ...rest }) {
  return (
    <span
      role="status"
      aria-live="polite"
      className={clsx('ui-spinner', `ui-spinner--${size}`, className)}
      {...rest}
    >
      <span className="ui-spinner__ring" aria-hidden="true" />
      <span className="ui-spinner__sr">{label}</span>
    </span>
  )
}
