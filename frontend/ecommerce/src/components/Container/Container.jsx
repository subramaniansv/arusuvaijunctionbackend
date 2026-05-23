/* ------------------------------------------------------------------
 * Container - max-width centered wrapper.
 * Sizes: sm | md | lg (default) | xl | full
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Container.css'

export default function Container({
  size = 'lg',
  className,
  as: Comp = 'div',
  children,
  ...rest
}) {
  return (
    <Comp className={clsx('ui-container', `ui-container--${size}`, className)} {...rest}>
      {children}
    </Comp>
  )
}
