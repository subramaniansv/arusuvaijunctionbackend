/* ------------------------------------------------------------------
 * EmptyState - friendly placeholder for empty lists.
 *
 * Usage:
 *   <EmptyState
 *     icon={<ShoppingCart/>}
 *     title="Your cart is empty"
 *     description="Add some delicious traditional products to get started."
 *     action={<Button as={Link} to="/products">Shop now</Button>}
 *   />
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './EmptyState.css'

export default function EmptyState({
  icon,
  title,
  description,
  action,
  className,
  ...rest
}) {
  return (
    <div className={clsx('ui-empty', className)} {...rest}>
      {icon && <div className="ui-empty__icon">{icon}</div>}
      {title && <h3 className="ui-empty__title">{title}</h3>}
      {description && <p className="ui-empty__desc">{description}</p>}
      {action && <div className="ui-empty__action">{action}</div>}
    </div>
  )
}
