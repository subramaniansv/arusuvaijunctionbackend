/**
 * Admin users page.
 *
 * GET  /api/user           -> list users (User { id, email, firstName, lastName, status, isAdmin })
 * PUT  /api/user?userId=&status=ACTIVE|INACTIVE|SUSPENDED|DEAD
 *
 * The current user cannot suspend their own account (basic guard
 * so we don't lock the only admin out of the console).
 */
import { useState } from 'react'
import toast from 'react-hot-toast'
import {
  USER_STATUSES,
  useAdminUsers,
  useUpdateUserStatus,
} from '../../lib/admin'
import { useAuthStore } from '../../stores/authStore'
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  Select,
  Skeleton,
} from '../../components'

const STATUS_VARIANT = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  SUSPENDED: 'warning',
  DEAD: 'danger',
}

function timeAgo(dateStr) {
  if (!dateStr) return 'Never'
  const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 1000)
  if (diff < 60) return 'Just now'
  if (diff < 3600) return `${Math.floor(diff / 60)} min ago`
  if (diff < 86400) return `${Math.floor(diff / 3600)} hr ago`
  const days = Math.floor(diff / 86400)
  if (days === 1) return '1 day ago'
  if (days < 30) return `${days} days ago`
  if (days < 365) {
    const months = Math.floor(days / 30)
    return months === 1 ? '1 month ago' : `${months} months ago`
  }
  const years = Math.floor(days / 365)
  return years === 1 ? '1 year ago' : `${years} years ago`
}

function StatusEditor({ user, disabled }) {
  const [status, setStatus] = useState(user.status || 'ACTIVE')
  const mut = useUpdateUserStatus()
  const dirty = status !== user.status

  const save = async () => {
    try {
      await mut.mutateAsync({ userId: user.id, status })
      toast.success(`${user.email} → ${status}`)
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not update user')
      setStatus(user.status)
    }
  }

  return (
    <div className="admin-status-editor">
      <Select
        aria-label="User status"
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        disabled={disabled || mut.isPending}
      >
        {USER_STATUSES.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </Select>
      <Button
        variant="primary"
        size="sm"
        disabled={disabled || !dirty}
        loading={mut.isPending}
        onClick={save}
      >
        Update
      </Button>
    </div>
  )
}

export default function AdminUsers() {
  const { data: users = [], isLoading, error } = useAdminUsers()
  const currentUserId = useAuthStore((s) => s.user?.userId)

  return (
    <div className="stack">
      <div className="admin-section__head">
        <div>
          <h2 className="admin-section__title">Users</h2>
          <p className="admin-section__hint">
            {users.length} account{users.length === 1 ? '' : 's'} on file.
          </p>
        </div>
      </div>

      {error && <Alert variant="danger">Could not load users.</Alert>}

      {isLoading ? (
        <div className="stack">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} height="64px" />
          ))}
        </div>
      ) : users.length === 0 ? (
        <EmptyState title="No users yet" />
      ) : (
        <div className="admin-table admin-table--users">
          <div className="admin-table__row admin-table__row--head">
            <span>User</span>
            <span>Email</span>
            <span>Role</span>
            <span>Last Login</span>
            <span>Current</span>
            <span>Change status</span>
          </div>
          {users.map((u) => {
            const isSelf = u.id === currentUserId
            const name =
              [u.firstName, u.lastName].filter(Boolean).join(' ') || '-'
            return (
              <div key={u.id} className="admin-table__row">
                <div className="admin-product-cell">
                  <div>
                    <div className="admin-product-cell__name">{name}</div>
                    <div className="admin-product-cell__id">
                      {String(u.id).slice(0, 8)}…
                      {isSelf && <span className="admin-self-tag"> (you)</span>}
                    </div>
                  </div>
                </div>
                <span className="text-muted">{u.email}</span>
                <Badge variant={u.admin ? 'primary' : 'neutral'}>
                  {u.admin ? 'Admin' : 'Customer'}
                </Badge>
                <span className="text-muted">{timeAgo(u.lastLogin)}</span>
                <Badge variant={STATUS_VARIANT[u.status] || 'neutral'}>
                  {u.status || 'ACTIVE'}
                </Badge>
                <StatusEditor user={u} disabled={isSelf} />
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
