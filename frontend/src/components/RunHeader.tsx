import { Ban, GitPullRequestArrow, Pause, Play, RotateCcw } from 'lucide-react'
import { elapsed, shortId } from '../format'
import type { RunSnapshot } from '../types'
import { StatusBadge } from './StatusBadge'

interface RunHeaderProps {
  run: RunSnapshot
  busy: boolean
  onPause: () => void
  onResume: () => void
  onCancel: () => void
  onRefresh: () => void
  onPrepareGitHub: () => void
}

export function RunHeader({ run, busy, onPause, onResume, onCancel, onRefresh, onPrepareGitHub }: RunHeaderProps) {
  const terminal = ['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED', 'CANCELLED'].includes(run.status)
  return (
    <header className="run-header">
      <div className="run-heading">
        <div className="run-heading-meta">
          <span className="mono">运行 {shortId(run.id)}</span>
          <StatusBadge status={run.status} />
          <span>{elapsed(run.createdAt, terminal ? run.updatedAt : undefined)}</span>
        </div>
        <h1>{run.requirement}</h1>
        <div className="workspace-path mono" title={run.workspace}>{run.workspace}</div>
      </div>
      <div className="run-actions" aria-label="运行控制">
        <button type="button" className="icon-button" onClick={onRefresh} disabled={busy} aria-label="刷新" title="刷新">
          <RotateCcw size={18} aria-hidden="true" />
        </button>
        {terminal && run.projectId && ['COMPLETED', 'COMPLETED_WITH_WARNINGS'].includes(run.status) && (
          <button type="button" className="button secondary" onClick={onPrepareGitHub} disabled={busy}>
            <GitPullRequestArrow size={17} aria-hidden="true" /> 创建推送审批
          </button>
        )}
        {run.status === 'PAUSED' ? (
          <button type="button" className="button secondary" onClick={onResume} disabled={busy}>
            <Play size={17} aria-hidden="true" /> 继续运行
          </button>
        ) : !terminal ? (
          <button type="button" className="button secondary" onClick={onPause} disabled={busy}>
            <Pause size={17} aria-hidden="true" /> 暂停运行
          </button>
        ) : null}
        {!terminal && (
          <button type="button" className="icon-button danger" onClick={onCancel} disabled={busy} aria-label="取消运行" title="取消运行">
            <Ban size={18} aria-hidden="true" />
          </button>
        )}
      </div>
    </header>
  )
}
