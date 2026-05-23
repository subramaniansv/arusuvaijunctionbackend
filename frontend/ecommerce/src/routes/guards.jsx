/**
 * Route guards.
 *
 * <ProtectedRoute />
 *   Wrap any route that requires a logged-in user. If not, redirect
 *   to /login and remember where the user was trying to go so we can
 *   send them back after a successful login.
 *
 * <PublicOnlyRoute />
 *   Inverse: routes only useful when logged OUT (login, register).
 *   Already-authenticated users go to home or the page they came from.
 *
 * <AdminRoute />
 *   Like ProtectedRoute but also requires the "admin" role on the JWT.
 */
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'

export function ProtectedRoute() {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const location = useLocation()
  if (!isAuthed) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <Outlet />
}

export function PublicOnlyRoute() {
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const location = useLocation()
  if (isAuthed) {
    const to = location.state?.from?.pathname || '/'
    return <Navigate to={to} replace />
  }
  return <Outlet />
}

export function AdminRoute() {
  // Separate selectors - returning an object literal here would
  // create a new snapshot identity on every store change and loop.
  const accessToken = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)
  const location = useLocation()
  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  const isAdmin = (user?.roles || []).includes('admin')
  if (!isAdmin) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
