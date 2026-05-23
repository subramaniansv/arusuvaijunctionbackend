/* ------------------------------------------------------------------
 * ShareModal — YouTube-style share sheet.
 *
 * Props:
 *   open        (bool)    - controlled visibility
 *   onClose     (fn)      - close handler
 *   url         (string)  - the share URL (defaults to current location)
 *   title       (string)  - product/page title used in share text
 *   text        (string)  - optional extra share text (e.g. price line)
 *
 * Behaviour:
 *   - On a device that supports navigator.share we surface a "More
 *     options" button that opens the native sheet (iOS/Android).
 *   - Always shows a horizontal strip of explicit social targets
 *     (WhatsApp, Telegram, Twitter/X, Facebook, Email) plus an
 *     Instagram entry that copies the link with a hint to paste it
 *     into the IG app (no IG web share URL exists).
 *   - Read-only URL field + "Copy" button with inline confirmation.
 * ------------------------------------------------------------------ */
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import {
  X,
  Copy,
  Check,
  Mail,
  Share2,
  Link as LinkIcon,
} from 'lucide-react'
import IconButton from '../IconButton/IconButton.jsx'
import Button from '../Button/Button.jsx'
import './ShareModal.css'

/* Brand-specific SVGs (lucide-react no longer ships trademarked
 * brand icons). Each renders inline using currentColor so the
 * surrounding `.share__btn-ic` circle controls the colour. */
const WhatsAppIcon = (props) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" {...props}>
    <path d="M19.05 4.91A10 10 0 0 0 4.31 18.34L3 22l3.77-1.27A10 10 0 1 0 19.05 4.91Zm-7 15.4a8.3 8.3 0 0 1-4.23-1.16l-.3-.18-2.24.75.75-2.18-.2-.32A8.3 8.3 0 1 1 12.05 20.3Zm4.55-6.2c-.25-.13-1.47-.72-1.7-.8s-.4-.13-.56.13-.65.8-.79.97-.29.19-.54.06a6.83 6.83 0 0 1-2-1.23 7.56 7.56 0 0 1-1.4-1.74c-.15-.25 0-.39.11-.51s.25-.29.37-.43.16-.25.25-.41.05-.32 0-.45-.55-1.34-.76-1.84-.4-.42-.55-.43h-.47a.91.91 0 0 0-.66.31 2.78 2.78 0 0 0-.87 2.07 4.83 4.83 0 0 0 1 2.57 11.07 11.07 0 0 0 4.22 3.74c.59.26 1.05.41 1.4.52a3.39 3.39 0 0 0 1.55.1 2.54 2.54 0 0 0 1.66-1.17 2.06 2.06 0 0 0 .14-1.17c-.06-.11-.22-.18-.47-.31Z"/>
  </svg>
)

const InstagramIcon = (props) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
    <rect x="2" y="2" width="20" height="20" rx="5" ry="5" />
    <path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37Z" />
    <line x1="17.5" y1="6.5" x2="17.51" y2="6.5" />
  </svg>
)

const TelegramIcon = (props) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" {...props}>
    <path d="M21.95 4.36a1.2 1.2 0 0 0-1.27-.18L2.83 11.4a1.2 1.2 0 0 0 .07 2.25l4.07 1.36 1.55 4.9a1.2 1.2 0 0 0 1.99.5l2.34-2.13 3.95 2.9a1.2 1.2 0 0 0 1.88-.73l3.6-15.06a1.2 1.2 0 0 0-.33-1.13ZM9.7 15.36l-.7 3.93-.92-3.34 8.92-7.94Z"/>
  </svg>
)

const TwitterIcon = (props) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" {...props}>
    <path d="M18.244 2H21.5l-7.39 8.45L23 22h-6.79l-5.32-6.96L4.8 22H1.54l7.91-9.04L1 2h6.94l4.81 6.36L18.244 2Zm-2.38 18h1.88L7.2 4H5.22l10.64 16Z"/>
  </svg>
)

const FacebookIcon = (props) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" {...props}>
    <path d="M22 12a10 10 0 1 0-11.56 9.88v-6.99H7.9V12h2.54V9.8c0-2.5 1.49-3.89 3.77-3.89 1.09 0 2.24.2 2.24.2v2.46h-1.26c-1.24 0-1.63.77-1.63 1.56V12h2.78l-.44 2.89h-2.34v6.99A10 10 0 0 0 22 12Z"/>
  </svg>
)

function buildTargets({ url, title, text }) {
  const enc = encodeURIComponent
  const shareText = text ? `${title} — ${text}` : title
  return [
    {
      key: 'whatsapp',
      label: 'WhatsApp',
      icon: <WhatsAppIcon />,
      className: 'share__btn--whatsapp',
      href: `https://wa.me/?text=${enc(`${shareText} ${url}`)}`,
    },
    {
      key: 'instagram',
      label: 'Instagram',
      icon: <InstagramIcon />,
      className: 'share__btn--instagram',
      // No web share URL — we copy + hint instead.
      action: 'copy-hint',
      hint: 'Link copied. Open Instagram and paste it in a DM or story.',
    },
    {
      key: 'telegram',
      label: 'Telegram',
      icon: <TelegramIcon />,
      className: 'share__btn--telegram',
      href: `https://t.me/share/url?url=${enc(url)}&text=${enc(shareText)}`,
    },
    {
      key: 'twitter',
      label: 'X / Twitter',
      icon: <TwitterIcon />,
      className: 'share__btn--twitter',
      href: `https://twitter.com/intent/tweet?url=${enc(url)}&text=${enc(shareText)}`,
    },
    {
      key: 'facebook',
      label: 'Facebook',
      icon: <FacebookIcon />,
      className: 'share__btn--facebook',
      href: `https://www.facebook.com/sharer/sharer.php?u=${enc(url)}`,
    },
    {
      key: 'email',
      label: 'Email',
      icon: <Mail size={22} />,
      className: 'share__btn--email',
      href: `mailto:?subject=${enc(title)}&body=${enc(`${shareText}\n\n${url}`)}`,
    },
  ]
}

async function copyToClipboard(value) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    /* fall through to legacy fallback */
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = value
    ta.setAttribute('readonly', '')
    ta.style.position = 'absolute'
    ta.style.left = '-9999px'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

export default function ShareModal({
  open,
  onClose,
  url,
  title = '',
  text = '',
}) {
  const shareUrl = url || (typeof window !== 'undefined' ? window.location.href : '')
  const [copied, setCopied] = useState(false)
  const [feedback, setFeedback] = useState('')
  const dialogRef = useRef(null)

  // Close on Escape + simple focus management.
  useEffect(() => {
    if (!open) return
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', onKey)
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // Focus dialog so screen readers announce it.
    queueMicrotask(() => dialogRef.current?.focus())
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = prev
    }
  }, [open, onClose])

  // Reset copied/hint state whenever the modal re-opens.
  useEffect(() => {
    if (open) {
      setCopied(false)
      setFeedback('')
    }
  }, [open])

  if (!open) return null

  const targets = buildTargets({ url: shareUrl, title, text })

  const flashFeedback = (msg) => {
    setFeedback(msg)
    window.setTimeout(() => setFeedback(''), 2500)
  }

  const handleCopy = async () => {
    const ok = await copyToClipboard(shareUrl)
    if (ok) {
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } else {
      flashFeedback("Couldn't copy — long-press the link to copy it manually.")
    }
  }

  const handleTarget = async (t) => {
    if (t.action === 'copy-hint') {
      const ok = await copyToClipboard(shareUrl)
      flashFeedback(
        ok
          ? t.hint
          : "Couldn't copy — long-press the link below to copy it manually.",
      )
      return
    }
    // External URL targets — open in a new tab/window.
    window.open(t.href, '_blank', 'noopener,noreferrer')
  }

  const handleNativeShare = async () => {
    try {
      await navigator.share({ title, text: text || title, url: shareUrl })
    } catch {
      /* user cancelled / unsupported — ignore */
    }
  }

  const canNativeShare =
    typeof navigator !== 'undefined' && typeof navigator.share === 'function'

  return createPortal(
    <div
      className="share__backdrop"
      onClick={(e) => {
        // Don't let the click bubble into ancestors (e.g. <Link>
        // wrappers on the ProductCard) and trigger navigation.
        e.stopPropagation()
        onClose?.()
      }}
      role="presentation"
    >
      <div
        ref={dialogRef}
        className="share__dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Share"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="share__head">
          <h2 className="share__title">Share</h2>
          <IconButton
            variant="ghost"
            aria-label="Close share dialog"
            onClick={onClose}
          >
            <X size={18} />
          </IconButton>
        </header>

        <ul className="share__targets" aria-label="Share to">
          {targets.map((t) => (
            <li key={t.key}>
              <button
                type="button"
                className={`share__btn ${t.className}`}
                onClick={() => handleTarget(t)}
              >
                <span className="share__btn-ic">{t.icon}</span>
                <span className="share__btn-label">{t.label}</span>
              </button>
            </li>
          ))}
        </ul>

        {feedback && (
          <p className="share__feedback" role="status">{feedback}</p>
        )}

        <div className="share__link">
          <span className="share__link-ic" aria-hidden="true">
            <LinkIcon size={16} />
          </span>
          <input
            type="text"
            readOnly
            value={shareUrl}
            onFocus={(e) => e.target.select()}
            aria-label="Share link"
            className="share__link-input"
          />
          <Button
            type="button"
            variant={copied ? 'secondary' : 'primary'}
            size="sm"
            leftIcon={copied ? <Check size={16} /> : <Copy size={16} />}
            onClick={handleCopy}
          >
            {copied ? 'Copied' : 'Copy'}
          </Button>
        </div>

        {canNativeShare && (
          <Button
            type="button"
            variant="ghost"
            fullWidth
            leftIcon={<Share2 size={16} />}
            onClick={handleNativeShare}
            className="share__more"
          >
            More options…
          </Button>
        )}
      </div>
    </div>,
    document.body,
  )
}
