/**
 * Admin: contact form submissions.
 *
 *   GET    /api/contact?limit=&offset=&status=
 *   PATCH  /api/contact?messageId=&status=
 *   DELETE /api/contact?messageId=
 *
 * Each row expands to show the full message. Admins can change
 * the workflow status (NEW → READ → REPLIED → ARCHIVED) and reply
 * directly via an `mailto:` shortcut.
 */
import { useState } from 'react'
import toast from 'react-hot-toast'
import { Mail, Phone, Trash2, MessageSquare } from 'lucide-react'

import {
  useAdminContactMessages,
  useDeleteContact,
  useUpdateContactStatus,
} from '../../lib/contact'
import {
  Alert, Badge, Button, EmptyState, Select, Skeleton,
} from '../../components'
import './admin.css'

const STATUSES = ['NEW', 'READ', 'REPLIED', 'ARCHIVED']

const STATUS_VARIANT = {
  NEW:      'warning',
  READ:     'primary',
  REPLIED:  'success',
  ARCHIVED: 'neutral',
}

function MessageRow({ msg }) {
  const [open, setOpen] = useState(msg.status === 'NEW')
  const [status, setStatus] = useState(msg.status || 'NEW')
  const update = useUpdateContactStatus()
  const del = useDeleteContact()

  const dirty = status !== msg.status

  const saveStatus = async (next) => {
    setStatus(next)
    try {
      await update.mutateAsync({ messageId: msg.messageId, status: next })
      toast.success(`Marked ${next}`)
    } catch (e) {
      setStatus(msg.status)
      toast.error(e?.response?.data?.message || 'Could not update')
    }
  }

  const onDelete = async () => {
    if (!confirm('Delete this message? This cannot be undone.')) return
    try {
      await del.mutateAsync(msg.messageId)
      toast.success('Message deleted')
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not delete')
    }
  }

  const created = msg.createdAt ? new Date(msg.createdAt).toLocaleString() : '—'

  return (
    <div className="admin-msg">
      <button
        type="button"
        className="admin-msg__head"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <div className="admin-msg__sender">
          <div className="admin-msg__name">{msg.name}</div>
          <div className="admin-msg__sub">{msg.subject || '(no subject)'}</div>
        </div>
        <div className="admin-msg__meta">
          <Badge variant={STATUS_VARIANT[status] || 'neutral'}>{status}</Badge>
          <span className="admin-msg__date">{created}</span>
        </div>
      </button>

      {open && (
        <div className="admin-msg__body">
          <div className="admin-msg__contacts">
            <a href={`mailto:${msg.email}`} className="staticpage__contact-row">
              <Mail size={16} /> {msg.email}
            </a>
            {msg.phone && (
              <a href={`tel:${msg.phone}`} className="staticpage__contact-row">
                <Phone size={16} /> {msg.phone}
              </a>
            )}
          </div>
          <p className="admin-msg__text">{msg.message}</p>

          <div className="admin-msg__actions">
            <Select
              aria-label="Status"
              value={status}
              onChange={(e) => saveStatus(e.target.value)}
              disabled={update.isPending}
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </Select>
            <Button
              as="a"
              href={`mailto:${msg.email}?subject=${encodeURIComponent(
                'Re: ' + (msg.subject || 'your message to Arusuvai')
              )}`}
              size="sm"
              leftIcon={<MessageSquare size={14} />}
            >
              Reply by email
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={onDelete}
              loading={del.isPending}
              leftIcon={<Trash2 size={14} />}
            >
              Delete
            </Button>
          </div>
          {dirty && update.isPending && (
            <span className="admin-msg__hint">Saving…</span>
          )}
        </div>
      )}
    </div>
  )
}

export default function AdminMessages() {
  const [status, setStatus] = useState('')
  const { data, isLoading, error } = useAdminContactMessages({ status })

  const messages = data?.messages || []
  const total = data?.total ?? messages.length

  return (
    <div className="stack">
      <div className="admin-section__head">
        <div>
          <h2 className="admin-section__title">Contact form</h2>
          <p className="admin-section__hint">
            {total} message{total === 1 ? '' : 's'}
            {status ? ` with status ${status}` : ''}.
          </p>
        </div>
        <Select
          aria-label="Filter status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          style={{ maxWidth: 200 }}
        >
          <option value="">All</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </Select>
      </div>

      {error && <Alert variant="danger">Could not load messages.</Alert>}

      {isLoading ? (
        <div className="stack">
          {[1, 2, 3].map((i) => <Skeleton key={i} height="72px" />)}
        </div>
      ) : messages.length === 0 ? (
        <EmptyState
          title="No messages"
          description="When customers fill in the contact form, their messages will show up here."
        />
      ) : (
        <div className="admin-msg-list">
          {messages.map((m) => <MessageRow key={m.messageId} msg={m} />)}
        </div>
      )}
    </div>
  )
}
