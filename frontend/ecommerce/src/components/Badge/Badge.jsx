/* ------------------------------------------------------------------
 * Badge - small label / pill.
 * Variants: neutral | success | warning | danger | info | accent | primary
 * Sizes:    sm | md
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Badge.css'

export default function Badge({
  variant = 'neutral',
  size = 'md',
  pill = true,
  className,
  children,
  ...rest
}) {
  return (
    <span
      className={clsx(
        'ui-badge',
        `ui-badge--${variant}`,
        `ui-badge--${size}`,
        pill && 'ui-badge--pill',
        className,
      )}
      {...rest}
    >
      {children}
    </span>
  )
}
