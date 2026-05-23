/* ------------------------------------------------------------------
 * Section - vertical rhythm wrapper with optional title row.
 * ------------------------------------------------------------------ */
import clsx from 'clsx'
import './Section.css'

export default function Section({
  title,
  subtitle,
  action,
  spacing = 'md',
  className,
  children,
  ...rest
}) {
  return (
    <section className={clsx('ui-section', `ui-section--${spacing}`, className)} {...rest}>
      {(title || action) && (
        <header className="ui-section__head">
          <div>
            {title && <h2 className="ui-section__title">{title}</h2>}
            {subtitle && <p className="ui-section__subtitle">{subtitle}</p>}
          </div>
          {action && <div className="ui-section__action">{action}</div>}
        </header>
      )}
      <div className="ui-section__body">{children}</div>
    </section>
  )
}
