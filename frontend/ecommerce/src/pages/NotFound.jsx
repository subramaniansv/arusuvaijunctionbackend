import { Link } from 'react-router-dom'
import Seo from '../components/Seo'

export default function NotFound() {
  return (
    <section className="stack">
      <Seo title="Page not found" noindex />
      <h1>404</h1>
      <p className="text-muted">Page not found.</p>
      <Link className="btn btn-primary" to="/">Go home</Link>
    </section>
  )
}
