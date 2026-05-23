/* ------------------------------------------------------------------
 * Skeleton - shimmer placeholder while content loads.
 * Pass `as`, `width`, `height`, `radius` or set className for layout.
 *
 * Usage:
 *   <Skeleton width="100%" height={180} />
 *   <Skeleton width="60%" height={14} radius="pill" />
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Skeleton.css'

export default function Skeleton({
  width,
  height,
  radius = 'md',
  className,
  as: Comp = 'span',
  style,
  ...rest
}) {
  return (
    <Comp
      aria-hidden="true"
      className={clsx('ui-skeleton', `ui-skeleton--${radius}`, className)}
      style={{ width, height, ...style }}
      {...rest}
    />
  )
}
