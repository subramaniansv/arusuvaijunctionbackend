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
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'

import RootLayout from './layouts/RootLayout'
import {
  ProtectedRoute,
  PublicOnlyRoute,
  AdminRoute,
} from './routes/guards'

import Home from './pages/Home'
import Products from './pages/Products'
import ProductDetail from './pages/ProductDetail'
import Auth from './pages/Auth'
import Cart from './pages/Cart'
import Checkout from './pages/Checkout'
import Orders from './pages/Orders'
import OrderDetail from './pages/OrderDetail'
import Account from './pages/Account'
import Wishlist from './pages/Wishlist'
import About from './pages/About'
import Contact from './pages/Contact'
import PrivacyPolicy from './pages/PrivacyPolicy'
import ReturnPolicy from './pages/ReturnPolicy'
import AdminLayout from './pages/admin/AdminLayout'
import AdminProducts from './pages/admin/AdminProducts'
import AdminProductNew from './pages/admin/AdminProductNew'
import AdminProductEdit from './pages/admin/AdminProductEdit'
import AdminOrders from './pages/admin/AdminOrders'
import AdminUsers from './pages/admin/AdminUsers'
import AdminMessages from './pages/admin/AdminMessages'
import NotFound from './pages/NotFound'

function App() {
  return (
    <BrowserRouter>
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
      <Toaster
        position="top-center"
        containerStyle={{ top: '50%', transform: 'translateY(-50%)' }}
        toastOptions={{ duration: 3500 }}
      />
    </BrowserRouter>
  )
}

export default App
