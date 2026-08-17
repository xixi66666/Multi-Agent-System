import type { RunStatus } from './types'

export function formatStatus(status: string) {
  return status.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function statusTone(status: RunStatus | string) {
  if (['COMPLETED', 'SUCCESS', 'APPROVED', 'EXECUTED'].includes(status)) return 'success'
  if (status === 'COMPLETED_WITH_WARNINGS' || status.includes('WAITING') || status === 'PAUSED' || status === 'PENDING') return 'warning'
  if (['FAILED', 'CANCELLED', 'NEEDS_ATTENTION', 'REJECTED'].includes(status)) return 'danger'
  if (status === 'RUNNING' || ['PLANNING', 'IMPLEMENTING', 'TESTING', 'REVIEWING'].includes(status)) return 'active'
  return 'neutral'
}

export function formatNumber(value: number) {
  return new Intl.NumberFormat('en-US').format(value)
}

export function formatTime(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function shortId(value: string) {
  return value.slice(0, 8)
}

export function elapsed(start: string, end?: string) {
  const milliseconds = Math.max(0, new Date(end ?? Date.now()).getTime() - new Date(start).getTime())
  const seconds = Math.floor(milliseconds / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  return `${minutes}m ${seconds % 60}s`
}
