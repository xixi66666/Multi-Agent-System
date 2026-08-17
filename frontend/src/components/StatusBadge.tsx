import { CircleAlert, CircleCheck, CircleDashed, CircleX, LoaderCircle } from 'lucide-react'
import { formatStatus, statusTone } from '../format'

export function StatusBadge({ status }: { status: string }) {
  const tone = statusTone(status)
  const Icon = tone === 'success'
    ? CircleCheck
    : tone === 'warning'
      ? CircleAlert
      : tone === 'danger'
        ? CircleX
        : tone === 'active'
          ? LoaderCircle
          : CircleDashed
  return (
    <span className={`status-badge status-${tone}`}>
      <Icon size={14} aria-hidden="true" className={tone === 'active' ? 'spin' : undefined} />
      {formatStatus(status)}
    </span>
  )
}
