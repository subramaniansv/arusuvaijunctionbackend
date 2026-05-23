/**
 * Route map.
 *
 * Public (no auth):
 *   /                          home
 *   /products                  catalog list
 *   /products/:productId       product detail
 *   /login                     login form        (PublicOnlyRoute - redirects if logged in)
 *   /register                  register form     (PublicOnlyRoute)
 *
 * Protected (requires access token):
 *   /cart                      current user's cart
 *   /orders                    order history
 *   /orders/:orderId           one order
 *   /account                   profile / addresses
 *
 * Admin (requires "admin" role on JWT):
 *   /admin/*                   admin console
 *
 * 404:
 *   *                          NotFound
 *
 * Auth flow:
 *   - Login posts to /auth?isLogin=true -> { accessToken, refreshToken }
 *   - Tokens persist via zustand -> localStorage.
 *   - axios attaches the access token and pre-emptively refreshes
 *     when it's <= 30s from expiry. Access token TTL is 1 day,
 *     refresh token TTL is 7 days, so a returning user keeps a
 *     seamless session for a week.
 *   - On 401 the interceptor refreshes once and retries; if refresh
 *     itself fails the store is cleared and <ProtectedRoute> sends
 *     the user to /login (remembering where they came from).
 */
import './App.css'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import { Analytics } from '@vercel/analytics/react'

import RootLayout from './layouts/RootLayout'
import {
  ProtectedRoute,
  PublicOnlyRoute,
  AdminRoute,
} from './routes/guards'

/* Eager: above-the-fold landing route. */
import Home from './pages/Home'

/* Lazy: every other route is split into its own chunk so the initial
   bundle stays small. React.lazy + Suspense lets Vite emit one chunk
   per page on `npm run build`. */
const Products        = lazy(() => import('./pages/Products'))
const ProductDetail   = lazy(() => import('./pages/ProductDetail'))
const Auth            = lazy(() => import('./pages/Auth'))
const Cart            = lazy(() => import('./pages/Cart'))
const Checkout        = lazy(() => import('./pages/Checkout'))
const Orders          = lazy(() => import('./pages/Orders'))
const OrderDetail     = lazy(() => import('./pages/OrderDetail'))
const Account         = lazy(() => import('./pages/Account'))
const Wishlist        = lazy(() => import('./pages/Wishlist'))
const About           = lazy(() => import('./pages/About'))
const Contact         = lazy(() => import('./pages/Contact'))
const PrivacyPolicy   = lazy(() => import('./pages/PrivacyPolicy'))
const ReturnPolicy    = lazy(() => import('./pages/ReturnPolicy'))
const AdminLayout       = lazy(() => import('./pages/admin/AdminLayout'))
const AdminProducts     = lazy(() => import('./pages/admin/AdminProducts'))
const AdminProductNew   = lazy(() => import('./pages/admin/AdminProductNew'))
const AdminProductEdit  = lazy(() => import('./pages/admin/AdminProductEdit'))
const AdminOrders       = lazy(() => import('./pages/admin/AdminOrders'))
const AdminUsers        = lazy(() => import('./pages/admin/AdminUsers'))
const AdminMessages     = lazy(() => import('./pages/admin/AdminMessages'))
const NotFound          = lazy(() => import('./pages/NotFound'))

/* Tiny placeholder while a route chunk is being fetched. Keep it
   visually empty so it doesn't shift layout. */
function RouteFallback() {
  return <div style={{ minHeight: '60vh' }} aria-busy="true" />
}

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route element={<RootLayout />}>
          {/* Public */}
          <Route index element={<Home />} />
          <Route path="products" element={<Products />} />
          <Route path="products/:productId" element={<ProductDetail />} />
          <Route path="about" element={<About />} />
          <Route path="contact" element={<Contact />} />
          <Route path="policy/privacy" element={<PrivacyPolicy />} />
          <Route path="policy/returns" element={<ReturnPolicy />} />

          {/* Public-only (hidden once logged in). Login + register
           * share the same Auth component; mode is derived from the
           * route path. */}
          <Route element={<PublicOnlyRoute />}>
            <Route path="login" element={<Auth />} />
            <Route path="register" element={<Auth />} />
          </Route>

          {/* Protected */}
          <Route element={<ProtectedRoute />}>
            <Route path="cart" element={<Cart />} />
            <Route path="checkout" element={<Checkout />} />
            <Route path="orders" element={<Orders />} />
            <Route path="orders/:orderId" element={<OrderDetail />} />
            <Route path="wishlist" element={<Wishlist />} />
            <Route path="account" element={<Account />} />
          </Route>

          {/* Admin */}
          <Route element={<AdminRoute />}>
            <Route path="admin" element={<AdminLayout />}>
              <Route path="products" element={<AdminProducts />} />
              <Route path="products/new" element={<AdminProductNew />} />
              <Route path="products/:id" element={<AdminProductEdit />} />
              <Route path="orders" element={<AdminOrders />} />
              <Route path="users" element={<AdminUsers />} />
              <Route path="messages" element={<AdminMessages />} />
            </Route>
          </Route>

          {/* 404 */}
          <Route path="*" element={<NotFound />} />
        </Route>
        </Routes>
      </Suspense>
      <Toaster
        position="top-center"
        toastOptions={{ duration: 3500 }}
      />
      <Analytics />
    </BrowserRouter>
  )
}

export default App
