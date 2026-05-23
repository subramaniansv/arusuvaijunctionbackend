/* ------------------------------------------------------------------
 * IconButton - square/round button that only contains an icon.
 * Variants: solid | ghost | outline
 * Shapes:   square (default) | round
 * Sizes:    sm | md | lg
 * Props:    aria-label REQUIRED (a11y - no visible text)
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './IconButton.css'

export default function IconButton({
  variant = 'ghost',
  shape = 'square',
  size = 'md',
  className,
  children,
  'aria-label': ariaLabel,
  ...rest
}) {
  if (!ariaLabel && process.env.NODE_ENV !== 'production') {
    // eslint-disable-next-line no-console
    console.warn('[IconButton] aria-label is required for accessibility')
  }
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      className={clsx(
        'ui-iconbtn',
        `ui-iconbtn--${variant}`,
        `ui-iconbtn--${shape}`,
        `ui-iconbtn--${size}`,
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  )
}
