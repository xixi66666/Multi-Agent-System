import { Check, GitPullRequestArrow, ShieldAlert, X } from 'lucide-react'
import { formatDateTime } from '../format'
import type { Approval } from '../types'
import { StatusBadge } from './StatusBadge'

interface ApprovalsPanelProps {
  approvals: Approval[]
  busy: boolean
  onDecision: (approvalId: string, decision: 'approve' | 'reject') => void
}

export function ApprovalsPanel({ approvals, busy, onDecision }: ApprovalsPanelProps) {
  return (
    <section className="approvals-panel" aria-label="外部操作审批">
      <header className="panel-heading">
        <div>
          <span className="section-label">外部边界</span>
          <h2>审批队列</h2>
        </div>
        <span className="panel-count">{approvals.filter((item) => item.status === 'PENDING').length} 项待审批</span>
      </header>
      <div className="approval-list">
        {approvals.map((approval) => (
          <article className="approval-row" key={approval.id}>
            <div className="approval-icon">
              {approval.actionType.includes('GITHUB') ? <GitPullRequestArrow size={20} /> : <ShieldAlert size={20} />}
            </div>
            <div className="approval-content">
              <div className="approval-heading">
                <div><strong>{approval.actionType}</strong><time>{formatDateTime(approval.requestedAt)}</time></div>
                <StatusBadge status={approval.status} />
              </div>
              <pre>{approval.requestPayload}</pre>
              {approval.status === 'PENDING' && (
                <div className="approval-actions">
                  <button type="button" className="button secondary" disabled={busy} onClick={() => onDecision(approval.id, 'reject')}>
                    <X size={17} aria-hidden="true" /> 拒绝
                  </button>
                  <button type="button" className="button primary" disabled={busy} onClick={() => onDecision(approval.id, 'approve')}>
                    <Check size={17} aria-hidden="true" /> 仅批准一次
                  </button>
                </div>
              )}
            </div>
          </article>
        ))}
        {approvals.length === 0 && <div className="empty-panel">暂无等待审批的外部操作</div>}
      </div>
    </section>
  )
}
