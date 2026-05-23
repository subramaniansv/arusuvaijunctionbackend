/* ------------------------------------------------------------------
 * PriceTag - renders an INR price with optional strike-through MRP
 * and a "x% off" badge.
 *
 * Props:
 *   amount  (required, number) - current selling price
 *   mrp     (number, optional) - original price; shows discount if > amount
 *   size    sm | md (default) | lg
 *   currency  default '₹'
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './PriceTag.css'

function format(num) {
  if (num == null || isNaN(num)) return ''
  return Number(num).toLocaleString('en-IN', { maximumFractionDigits: 2 })
}

export default function PriceTag({
  amount,
  mrp,
  size = 'md',
  currency = '\u20B9',
  className,
  ...rest
}) {
  const showMrp = typeof mrp === 'number' && mrp > amount
  const discount = showMrp ? Math.round(((mrp - amount) / mrp) * 100) : null

  return (
    <span className={clsx('ui-price', `ui-price--${size}`, className)} {...rest}>
      <span className="ui-price__amount">
        <span className="ui-price__currency">{currency}</span>
        {format(amount)}
      </span>
      {showMrp && (
        <>
          <span className="ui-price__mrp">{currency}{format(mrp)}</span>
          <span className="ui-price__discount">{discount}% off</span>
        </>
      )}
    </span>
  )
}
