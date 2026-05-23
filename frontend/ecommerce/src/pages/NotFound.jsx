import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <section className="stack">
      <h1>404</h1>
      <p className="text-muted">Page not found.</p>
      <Link className="btn btn-primary" to="/">Go home</Link>
    </section>
  )
}
