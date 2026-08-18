import type { RunStatus } from './types'

export function formatStatus(status: string) {
  const labels: Record<string, string> = {
    CREATED: '已创建',
    PLANNING: '正在规划',
    IMPLEMENTING: '正在开发',
    TESTING: '正在测试',
    REVIEWING: '正在审查',
    WAITING_FOR_INPUT: '等待输入',
    WAITING_FOR_APPROVAL: '等待审批',
    PAUSED: '已暂停',
    NEEDS_ATTENTION: '需要处理',
    COMPLETED: '已完成',
    COMPLETED_WITH_WARNINGS: '完成但有警告',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    RUNNING: '运行中',
    SUCCESS: '成功',
    PENDING: '待审批',
    APPROVED: '已批准',
    REJECTED: '已拒绝',
    EXECUTED: '已执行',
  }
  return labels[status] ?? status.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function statusTone(status: RunStatus | string) {
  if (['COMPLETED', 'SUCCESS', 'APPROVED', 'EXECUTED'].includes(status)) return 'success'
  if (status === 'COMPLETED_WITH_WARNINGS' || status.includes('WAITING') || status === 'PAUSED' || status === 'PENDING') return 'warning'
  if (['FAILED', 'CANCELLED', 'NEEDS_ATTENTION', 'REJECTED'].includes(status)) return 'danger'
  if (status === 'RUNNING' || ['PLANNING', 'IMPLEMENTING', 'TESTING', 'REVIEWING'].includes(status)) return 'active'
  return 'neutral'
}

export function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

export function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
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
  if (seconds < 60) return `${seconds} 秒`
  const minutes = Math.floor(seconds / 60)
  return `${minutes} 分 ${seconds % 60} 秒`
}
