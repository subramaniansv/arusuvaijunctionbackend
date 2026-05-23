/* ------------------------------------------------------------------
 * Avatar - circular image with initials fallback.
 * Sizes: xs | sm | md | lg | xl
 * ------------------------------------------------------------------ */
import { useState } from 'react'
import clsx from 'clsx'
import './Avatar.css'

function getInitials(name) {
  if (!name) return '?'
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase()
}

export default function Avatar({ src, name = '', size = 'md', className, ...rest }) {
  const [err, setErr] = useState(false)
  const showImage = src && !err
  return (
    <span
      className={clsx('ui-avatar', `ui-avatar--${size}`, className)}
      aria-label={name || 'User'}
      {...rest}
    >
      {showImage ? (
        <img
          src={src}
          alt={name}
          className="ui-avatar__img"
          onError={() => setErr(true)}
          loading="lazy"
          decoding="async"
        />
      ) : (
        <span className="ui-avatar__initials">{getInitials(name)}</span>
      )}
    </span>
  )
}
