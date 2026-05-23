/**
 * Admin orders page.
 *
 * Lists every order returned by GET /api/admin?type=order with the
 * customer's email/name (when present), totals, item count, and an
 * inline status changer. Hitting "Update" calls PUT /api/admin with
 * the new status; the backend handles stock refunds on CANCELLED.
 */
import { useState } from 'react'
import { Link } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  ORDER_STATUSES,
  useAdminOrders,
  useUpdateOrderStatus,
} from '../../lib/admin'
import {
  formatOrderDate,
  shortOrderId,
  ORDER_STATUS_VARIANT,
} from '../../lib/orders'
import {
  Alert,
  Badge,
  Button,
  EmptyState,
  PriceTag,
  Select,
  Skeleton,
} from '../../components'

function StatusEditor({ order }) {
  const [status, setStatus] = useState(order.status)
  const mut = useUpdateOrderStatus()
  const dirty = status !== order.status

  const save = async () => {
    try {
      await mut.mutateAsync({ orderId: order.orderId, status })
      toast.success(`Order ${shortOrderId(order.orderId)} → ${status}`)
    } catch (e) {
      toast.error(e?.response?.data?.message || 'Could not update status')
      setStatus(order.status)
    }
  }

  return (
    <div className="admin-status-editor">
      <Select
        aria-label="Order status"
        value={status}
        onChange={(e) => setStatus(e.target.value)}
        disabled={mut.isPending}
      >
        {ORDER_STATUSES.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </Select>
      <Button
        variant="primary"
        size="sm"
        disabled={!dirty}
        loading={mut.isPending}
        onClick={save}
      >
        Update
      </Button>
    </div>
  )
}

export default function AdminOrders() {
  const { data: orders = [], isLoading, error } = useAdminOrders({ limit: 100 })

  return (
    <div className="stack">
      <div className="admin-section__head">
        <div>
          <h2 className="admin-section__title">Orders</h2>
          <p className="admin-section__hint">
            {orders.length} order{orders.length === 1 ? '' : 's'} found.
          </p>
        </div>
      </div>

      {error && <Alert variant="danger">Could not load orders.</Alert>}

      {isLoading ? (
        <div className="stack">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} height="72px" />
          ))}
        </div>
      ) : orders.length === 0 ? (
        <EmptyState
          title="No orders yet"
          description="Once customers place orders, they will appear here."
        />
      ) : (
        <div className="admin-table admin-table--orders">
          <div className="admin-table__row admin-table__row--head">
            <span>Order</span>
            <span>Placed</span>
            <span>Items</span>
            <span>Total</span>
            <span>Current</span>
            <span>Change status</span>
          </div>
          {orders.map((o) => {
            const items = o.orderItems || []
            const itemCount = items.reduce(
              (n, it) => n + (Number(it.quantity) || 0),
              0,
            )
            return (
              <div key={o.orderId} className="admin-table__row">
                <div className="admin-product-cell">
                  <div>
                    <div className="admin-product-cell__name">
                      <Link to={`/orders/${o.orderId}`}>
                        {shortOrderId(o.orderId)}
                      </Link>
                    </div>
                    <div className="admin-product-cell__id">
                      user {String(o.userId || '').slice(0, 8)}…
                    </div>
                  </div>
                </div>
                <span className="text-muted">{formatOrderDate(o.orderedAt)}</span>
                <span>
                  {itemCount} item{itemCount === 1 ? '' : 's'}
                </span>
                <PriceTag amount={Number(o.totalAmount) || 0} />
                <Badge variant={ORDER_STATUS_VARIANT[o.status] || 'neutral'}>
                  {o.status}
                </Badge>
                <StatusEditor order={o} />
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
