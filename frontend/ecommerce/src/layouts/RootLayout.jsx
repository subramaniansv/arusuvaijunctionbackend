/**
 * Root shell: top nav + outlet.
 *
 * Layout
 * ------
 *   Desktop (>= 960px):
 *     [logo] [search] [Products] [Wishlist] [Orders] [Account] [Admin] [Cart] [Logout]
 *     Each nav item is icon + label.
 *
 *   Tablet / mobile (< 960px):
 *     [hamburger] [logo] ............................. [search] [cart]
 *     The full menu collapses into a slide-in drawer (YouTube-mobile style)
 *     opened by the hamburger; search becomes a toggle that reveals an
 *     inline row below the bar.
 */
import { useEffect, useRef, useState } from 'react'
import {
  Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams,
} from 'react-router-dom'
import {
  Search, ShoppingBag, ShoppingCart, Package, LayoutDashboard,
  LogOut, LogIn, X, UserCircle, Heart, Menu, MailWarning, Loader2,
} from 'lucide-react'
import toast from 'react-hot-toast'

import { useAuthStore } from '../stores/authStore'
import { useCartItemCount } from '../lib/cart'
import { useMyProfile, useResendVerification } from '../lib/me'
import Avatar from '../components/Avatar/Avatar.jsx'
import './RootLayout.css'

export default function RootLayout() {
  const user = useAuthStore((s) => s.user)
  const isAuthed = useAuthStore((s) => !!s.accessToken)
  const clear = useAuthStore((s) => s.clear)
  const cartCount = useCartItemCount()
  // Profile is loaded only when signed in - drives the unverified-email
  // CTA in the navbar. Failures fail silently (the CTA simply won't show).
  const { data: profile } = useMyProfile()
  const resendVerify = useResendVerification()
  const navigate = useNavigate()
  const location = useLocation()

  const path = location.pathname
  const isAuth = path === '/login' || path === '/register'
  const isBare = path === '/' || isAuth

  // ---- search state ----
  const onProducts = path === '/products'
  const [urlParams] = useSearchParams()
  const [query, setQuery] = useState(onProducts ? (urlParams.get('q') || '') : '')
  useEffect(() => {
    if (onProducts) setQuery(urlParams.get('q') || '')
  }, [onProducts, urlParams])

  // ---- mobile UI state ----
  const [mobileSearchOpen, setMobileSearchOpen] = useState(false)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [accountMenuOpen, setAccountMenuOpen] = useState(false)
  const mobileInputRef = useRef(null)
  const accountMenuRef = useRef(null)

  useEffect(() => {
    if (mobileSearchOpen) mobileInputRef.current?.focus()
  }, [mobileSearchOpen])

  // close drawer on route change
  useEffect(() => { setDrawerOpen(false); setAccountMenuOpen(false) }, [path])

  // click-away + ESC to close account dropdown
  useEffect(() => {
    if (!accountMenuOpen) return undefined
    const onDown = (e) => {
      if (accountMenuRef.current && !accountMenuRef.current.contains(e.target)) {
        setAccountMenuOpen(false)
      }
    }
    const onKey = (e) => { if (e.key === 'Escape') setAccountMenuOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [accountMenuOpen])

  // close drawer on ESC + lock body scroll while open
  useEffect(() => {
    if (!drawerOpen) return
    const onKey = (e) => { if (e.key === 'Escape') setDrawerOpen(false) }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [drawerOpen])

  const submitSearch = (e) => {
    e?.preventDefault()
    const q = query.trim()
    const target = q ? `/products?q=${encodeURIComponent(q)}` : '/products'
    navigate(target)
    setMobileSearchOpen(false)
  }

  const onLogout = () => {
    clear()
    setDrawerOpen(false)
    navigate('/login', { replace: true })
  }

  const isAdmin = user?.roles?.includes('admin')
  const needsVerify = isAuthed && profile && profile.emailVerified === false

  const handleResendVerify = async (closeDrawer) => {
    closeDrawer?.()
    try {
      await resendVerify.mutateAsync()
      toast.success('Verification email sent. Check your inbox.')
    } catch (err) {
      toast.error(
        err?.response?.data?.message ||
        err?.message ||
        'Could not send verification email',
      )
    }
  }

  /* Shared nav items - rendered both in the desktop cluster and inside
     the mobile drawer.  When closeDrawer is provided, it's called on click. */
  const renderNavItems = (closeDrawer) => (
    <>
      <NavLink
        to="/products"
        end
        className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
        onClick={closeDrawer}
      >
        <ShoppingBag size={20} aria-hidden="true" />
        <span>Products</span>
      </NavLink>

      {isAuthed && (
        <NavLink
          to="/wishlist"
          className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
          onClick={closeDrawer}
        >
          <Heart size={20} aria-hidden="true" />
          <span>Wishlist</span>
        </NavLink>
      )}

      {isAuthed && (
        <NavLink
          to="/orders"
          className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
          onClick={closeDrawer}
        >
          <Package size={20} aria-hidden="true" />
          <span>Orders</span>
        </NavLink>
      )}

      {needsVerify && (
        <button
          type="button"
          className="nav__item nav__item--verify"
          onClick={() => handleResendVerify(closeDrawer)}
          disabled={resendVerify.isPending}
          title={`Resend verification email to ${profile?.email || 'your inbox'}`}
        >
          {resendVerify.isPending
            ? <Loader2 size={18} className="spin" aria-hidden="true" />
            : <MailWarning size={18} aria-hidden="true" />}
          <span>Verify email</span>
        </button>
      )}

      {isAuthed && (
        <NavLink
          to="/account"
          className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
          onClick={closeDrawer}
        >
          <UserCircle size={20} aria-hidden="true" />
          <span>Account</span>
        </NavLink>
      )}

      {isAdmin && (
        <NavLink
          to="/admin"
          className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
          onClick={closeDrawer}
        >
          <LayoutDashboard size={20} aria-hidden="true" />
          <span>Admin</span>
        </NavLink>
      )}

      <NavLink
        to="/cart"
        className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
        onClick={closeDrawer}
        aria-label={
          isAuthed && cartCount > 0
            ? `Cart, ${cartCount} item${cartCount === 1 ? '' : 's'}`
            : 'Cart'
        }
      >
        <span className="nav__item-iconwrap">
          <ShoppingCart size={20} aria-hidden="true" />
          {isAuthed && cartCount > 0 && (
            <span className="nav__badge" aria-hidden="true">
              {cartCount > 99 ? '99+' : cartCount}
            </span>
          )}
        </span>
        <span>Cart</span>
      </NavLink>

      {isAuthed ? (
        <button
          type="button"
          className="nav__item nav__item--btn"
          onClick={() => { closeDrawer?.(); onLogout() }}
        >
          <LogOut size={20} aria-hidden="true" />
          <span>Logout</span>
        </button>
      ) : (
        <Link
          to="/login"
          className="nav__item nav__item--cta"
          onClick={closeDrawer}
        >
          <LogIn size={18} aria-hidden="true" />
          <span>Login</span>
        </Link>
      )}
    </>
  )

  return (
    <>
      {!isAuth && (
        <header className="nav">
          <div className="nav__inner container">
            {/* hamburger - visible only below desktop breakpoint */}
            <button
              type="button"
              className="nav__icon nav__hamburger"
              aria-label="Open menu"
              aria-expanded={drawerOpen}
              aria-controls="nav-drawer"
              onClick={() => setDrawerOpen(true)}
            >
              <Menu size={22} aria-hidden="true" />
            </button>

            {/* logo */}
            <Link to="/" className="nav__logo" aria-label="Arusuvai home">
              <span className="nav__logo-mark" aria-hidden="true">A</span>
              <span className="nav__logo-text">Arusuvai</span>
            </Link>

            {/* inline search (collapses below 720px to mobile toggle) */}
            <form
              className="nav__search"
              role="search"
              onSubmit={submitSearch}
              aria-label="Search products"
            >
              <Search size={16} aria-hidden="true" />
              <input
                type="search"
                placeholder="Search murukku, sweets, mixture..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search products"
              />
              {query && (
                <button
                  type="button"
                  className="nav__search-clear"
                  aria-label="Clear search"
                  onClick={() => setQuery('')}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              )}
            </form>

            {/* desktop nav cluster (>= 960px):
                 Products + Cart + Avatar dropdown. Everything else
                 (Wishlist, Orders, Account, Admin, Verify, Logout)
                 lives inside the avatar menu to keep the bar tidy. */}
            <nav className="nav__cluster" aria-label="Primary">
              <NavLink
                to="/products"
                end
                className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
              >
                <ShoppingBag size={20} aria-hidden="true" />
                <span>Products</span>
              </NavLink>

              <NavLink
                to="/cart"
                className={({ isActive }) => `nav__item${isActive ? ' is-active' : ''}`}
                aria-label={
                  isAuthed && cartCount > 0
                    ? `Cart, ${cartCount} item${cartCount === 1 ? '' : 's'}`
                    : 'Cart'
                }
              >
                <span className="nav__item-iconwrap">
                  <ShoppingCart size={20} aria-hidden="true" />
                  {isAuthed && cartCount > 0 && (
                    <span className="nav__badge" aria-hidden="true">
                      {cartCount > 99 ? '99+' : cartCount}
                    </span>
                  )}
                </span>
                <span>Cart</span>
              </NavLink>

              {isAuthed ? (
                <div className="nav__account" ref={accountMenuRef}>
                  <button
                    type="button"
                    className="nav__avatar-btn"
                    aria-haspopup="menu"
                    aria-expanded={accountMenuOpen}
                    aria-label="Account menu"
                    onClick={() => setAccountMenuOpen((v) => !v)}
                  >
                    <Avatar
                      name={user?.firstName || user?.email || 'User'}
                      size="sm"
                    />
                  </button>

                  {accountMenuOpen && (
                    <div
                      className="nav__menu"
                      role="menu"
                      aria-label="Account"
                    >
                      {user && (
                        <div className="nav__menu-head">
                          <div className="nav__menu-name">
                            {user.firstName || user.email || 'My account'}
                          </div>
                          {user.email && (
                            <div className="nav__menu-email">{user.email}</div>
                          )}
                        </div>
                      )}

                      <NavLink
                        to="/orders"
                        className="nav__menu-item"
                        role="menuitem"
                        onClick={() => setAccountMenuOpen(false)}
                      >
                        <Package size={18} aria-hidden="true" />
                        <span>Orders</span>
                      </NavLink>

                      <NavLink
                        to="/account"
                        className="nav__menu-item"
                        role="menuitem"
                        onClick={() => setAccountMenuOpen(false)}
                      >
                        <UserCircle size={18} aria-hidden="true" />
                        <span>User profile</span>
                      </NavLink>

                      <NavLink
                        to="/wishlist"
                        className="nav__menu-item"
                        role="menuitem"
                        onClick={() => setAccountMenuOpen(false)}
                      >
                        <Heart size={18} aria-hidden="true" />
                        <span>Wishlist</span>
                      </NavLink>

                      {isAdmin && (
                        <NavLink
                          to="/admin"
                          className="nav__menu-item"
                          role="menuitem"
                          onClick={() => setAccountMenuOpen(false)}
                        >
                          <LayoutDashboard size={18} aria-hidden="true" />
                          <span>Admin</span>
                        </NavLink>
                      )}

                      {needsVerify && (
                        <button
                          type="button"
                          className="nav__menu-item nav__menu-item--verify"
                          role="menuitem"
                          onClick={() => handleResendVerify(() => setAccountMenuOpen(false))}
                          disabled={resendVerify.isPending}
                        >
                          {resendVerify.isPending
                            ? <Loader2 size={16} className="spin" aria-hidden="true" />
                            : <MailWarning size={16} aria-hidden="true" />}
                          <span>Verify email</span>
                        </button>
                      )}

                      <div className="nav__menu-sep" role="separator" />

                      <button
                        type="button"
                        className="nav__menu-item"
                        role="menuitem"
                        onClick={() => { setAccountMenuOpen(false); onLogout() }}
                      >
                        <LogOut size={18} aria-hidden="true" />
                        <span>Logout</span>
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <Link to="/login" className="nav__item nav__item--cta">
                  <LogIn size={18} aria-hidden="true" />
                  <span>Login</span>
                </Link>
              )}
            </nav>

            {/* mobile right-side cluster: search toggle + cart */}
            <div className="nav__cluster-mobile">
              <button
                type="button"
                className="nav__icon"
                aria-label="Search"
                aria-expanded={mobileSearchOpen}
                onClick={() => setMobileSearchOpen((v) => !v)}
              >
                <Search size={20} aria-hidden="true" />
              </button>

              <NavLink
                to="/cart"
                className={({ isActive }) =>
                  `nav__icon${isActive ? ' is-active' : ''}`
                }
                aria-label={
                  isAuthed && cartCount > 0
                    ? `Cart, ${cartCount} item${cartCount === 1 ? '' : 's'}`
                    : 'Cart'
                }
              >
                <ShoppingCart size={20} aria-hidden="true" />
                {isAuthed && cartCount > 0 && (
                  <span className="nav__badge" aria-hidden="true">
                    {cartCount > 99 ? '99+' : cartCount}
                  </span>
                )}
              </NavLink>
            </div>
          </div>

          {/* mobile expandable search row */}
          <div
            className="nav__search-row container"
            hidden={!mobileSearchOpen}
          >
            <form
              className="nav__search nav__search--row"
              role="search"
              onSubmit={submitSearch}
              aria-label="Search products"
            >
              <Search size={16} aria-hidden="true" />
              <input
                ref={mobileInputRef}
                type="search"
                placeholder="Search products..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="Search products"
              />
              {query && (
                <button
                  type="button"
                  className="nav__search-clear"
                  aria-label="Clear search"
                  onClick={() => setQuery('')}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              )}
            </form>
          </div>
        </header>
      )}

      {/* slide-in drawer (mobile / tablet) */}
      {!isAuth && (
        <>
          <div
            className={`nav__backdrop${drawerOpen ? ' is-open' : ''}`}
            onClick={() => setDrawerOpen(false)}
            aria-hidden="true"
          />
          <aside
            id="nav-drawer"
            className={`nav__drawer${drawerOpen ? ' is-open' : ''}`}
            aria-label="Menu"
            aria-hidden={!drawerOpen}
          >
            <div className="nav__drawer-head">
              <Link
                to="/"
                className="nav__logo"
                onClick={() => setDrawerOpen(false)}
              >
                <span className="nav__logo-mark" aria-hidden="true">A</span>
                <span>Arusuvai</span>
              </Link>
              <button
                type="button"
                className="nav__icon"
                aria-label="Close menu"
                onClick={() => setDrawerOpen(false)}
              >
                <X size={20} aria-hidden="true" />
              </button>
            </div>

            {isAuthed && user && (
              <div className="nav__drawer-user">
                <UserCircle size={28} aria-hidden="true" />
                <div>
                  <div className="nav__drawer-user-name">
                    {user.firstName || user.email || 'My account'}
                  </div>
                  {user.email && (
                    <div className="nav__drawer-user-email">{user.email}</div>
                  )}
                </div>
              </div>
            )}

            <nav className="nav__drawer-list" aria-label="Primary">
              {renderNavItems(() => setDrawerOpen(false))}
            </nav>
          </aside>
        </>
      )}

      <main
        className={isBare ? undefined : 'container'}
        style={isBare ? undefined : { paddingBlock: 'var(--space-8)' }}
      >
        <Outlet />
      </main>
    </>
  )
}
