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
          <span className="mono">RUN {shortId(run.id)}</span>
          <StatusBadge status={run.status} />
          <span>{elapsed(run.createdAt, terminal ? run.updatedAt : undefined)}</span>
        </div>
        <h1>{run.requirement}</h1>
        <div className="workspace-path mono" title={run.workspace}>{run.workspace}</div>
      </div>
      <div className="run-actions" aria-label="Run controls">
        <button type="button" className="icon-button" onClick={onRefresh} disabled={busy} aria-label="Refresh" title="Refresh">
          <RotateCcw size={18} aria-hidden="true" />
        </button>
        {terminal && run.projectId && ['COMPLETED', 'COMPLETED_WITH_WARNINGS'].includes(run.status) && (
          <button type="button" className="button secondary" onClick={onPrepareGitHub} disabled={busy}>
            <GitPullRequestArrow size={17} aria-hidden="true" /> Prepare push
          </button>
        )}
        {run.status === 'PAUSED' ? (
          <button type="button" className="button secondary" onClick={onResume} disabled={busy}>
            <Play size={17} aria-hidden="true" /> Resume
          </button>
        ) : !terminal ? (
          <button type="button" className="button secondary" onClick={onPause} disabled={busy}>
            <Pause size={17} aria-hidden="true" /> Pause
          </button>
        ) : null}
        {!terminal && (
          <button type="button" className="icon-button danger" onClick={onCancel} disabled={busy} aria-label="Cancel run" title="Cancel run">
            <Ban size={18} aria-hidden="true" />
          </button>
        )}
      </div>
    </header>
  )
}
