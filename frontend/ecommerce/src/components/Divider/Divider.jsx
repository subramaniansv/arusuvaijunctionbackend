/* ------------------------------------------------------------------
 * Divider - horizontal or vertical hairline.
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Divider.css'

export default function Divider({ orientation = 'horizontal', className, ...rest }) {
  return (
    <hr
      role="separator"
      aria-orientation={orientation}
      className={clsx('ui-divider', `ui-divider--${orientation}`, className)}
      {...rest}
    />
  )
}
