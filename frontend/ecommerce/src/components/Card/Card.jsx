/* ------------------------------------------------------------------
 * Card - generic surface. Used as a building block by ProductCard,
 * ReviewCard, OrderSummaryCard, etc.
 *
 * Variants:  flat | elevated | outlined
 * Padding:   none | sm | md (default) | lg
 *
 * Usage:
 *   <Card>...</Card>
 *   <Card variant="elevated" padding="lg">...</Card>
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Card.css'

export default function Card({
  variant = 'outlined',
  padding = 'md',
  interactive = false,
  className,
  as: Comp = 'div',
  children,
  ...rest
}) {
  return (
    <Comp
      className={clsx(
        'ui-card',
        `ui-card--${variant}`,
        `ui-card--pad-${padding}`,
        interactive && 'ui-card--interactive',
        className,
      )}
      {...rest}
    >
      {children}
    </Comp>
  )
}
