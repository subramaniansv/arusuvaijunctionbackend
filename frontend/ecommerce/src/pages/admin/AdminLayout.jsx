/**
 * Admin console layout.
 *
 * Sub-nav across the top, nested routes render in the Outlet.
 * The Admin route guard already enforces the role, so this file
 * only has to handle layout + navigation.
 */
import { NavLink, Outlet, Navigate, useLocation } from 'react-router-dom'
import { Box, Package, ShoppingBag, Users, Inbox } from 'lucide-react'
import { Container } from '../../components'
import './admin.css'

const TABS = [
  { to: 'products', label: 'Products', icon: Package },
  { to: 'orders',   label: 'Orders',   icon: ShoppingBag },
  { to: 'users',    label: 'Users',    icon: Users },
  { to: 'messages', label: 'Messages', icon: Inbox },
]

export default function AdminLayout() {
  const location = useLocation()

  // /admin -> /admin/products (default landing). Using Navigate with
  // a key here so it only fires on the exact /admin path, not the
  // nested ones.
  if (location.pathname === '/admin' || location.pathname === '/admin/') {
    return <Navigate to="/admin/products" replace />
  }

  return (
    <div className="admin">
      <Container size="xl">
        <header className="admin__head">
          <div className="admin__title-row">
            <Box size={20} aria-hidden="true" />
            <h1 className="admin__title">Admin Console</h1>
          </div>
          <p className="admin__subtitle">
            Manage the catalog, monitor orders and review accounts.
          </p>
        </header>

        <nav className="admin__tabs" aria-label="Admin sections">
          {TABS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                'admin__tab' + (isActive ? ' is-active' : '')
              }
            >
              <Icon size={16} aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <section className="admin__panel">
          <Outlet />
        </section>
      </Container>
    </div>
  )
}
