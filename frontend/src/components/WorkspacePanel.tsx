import { GitBranch, HardDrive, Network } from 'lucide-react'
import { formatTime, shortId } from '../format'
import type { TaskWorkspace } from '../types'

export function WorkspacePanel({ workspaces }: { workspaces: TaskWorkspace[] }) {
  return (
    <section className="workspace-panel" aria-label="Git 工作区">
      <header className="panel-heading">
        <div>
          <span className="section-label">隔离环境</span>
          <h2>Git 工作区</h2>
        </div>
        <span className="panel-count">{workspaces.length} 个工作树</span>
      </header>
      <div className="workspace-list">
        {workspaces.map((workspace) => (
          <article className="workspace-row" key={workspace.id}>
            <div className="workspace-kind">{workspace.type === 'INTEGRATION' ? <Network size={18} /> : <HardDrive size={18} />}</div>
            <div className="workspace-details">
              <div><strong>{workspace.type === 'INTEGRATION' ? '集成工作区' : `任务 ${workspace.taskId ? shortId(workspace.taskId) : ''}`}</strong><time>{formatTime(workspace.createdAt)}</time></div>
              <span className="mono path-value" title={workspace.path}>{workspace.path}</span>
              <span className="branch-value"><GitBranch size={14} aria-hidden="true" /> <span className="mono">{workspace.branchName}</span></span>
            </div>
          </article>
        ))}
        {workspaces.length === 0 && <div className="empty-panel">本次运行尚未创建工作树</div>}
      </div>
    </section>
  )
}
