import { Blocks, Bot, BrainCircuit, CheckCircle2, FlaskConical, GitMerge, SearchCode, ShieldCheck } from 'lucide-react'
import { formatTime } from '../format'
import type { AgentTask } from '../types'
import { StatusBadge } from './StatusBadge'

function roleIcon(role: string) {
  const props = { size: 18, 'aria-hidden': true as const }
  if (role === 'PLANNER') return <BrainCircuit {...props} />
  if (role === 'ARCHITECT') return <Blocks {...props} />
  if (role === 'IMPLEMENTER') return <Bot {...props} />
  if (role === 'TESTER') return <FlaskConical {...props} />
  if (role === 'REVIEWER') return <ShieldCheck {...props} />
  if (role === 'INTEGRATOR') return <GitMerge {...props} />
  if (role === 'RESEARCHER') return <SearchCode {...props} />
  return <CheckCircle2 {...props} />
}

export function AgentFlow({ tasks }: { tasks: AgentTask[] }) {
  return (
    <section className="flow-panel" aria-label="Agent execution flow">
      <header className="panel-heading">
        <div>
          <span className="section-label">Execution graph</span>
          <h2>Agent dispatch</h2>
        </div>
        <span className="panel-count">{tasks.length} tasks</span>
      </header>
      <div className="agent-flow">
        {tasks.map((task, index) => (
          <article className="agent-row" key={task.id}>
            <div className="flow-rail" aria-hidden="true">
              <span className={`flow-node ${task.status.toLowerCase()}`}>{roleIcon(task.role)}</span>
              {index < tasks.length - 1 && <span className="flow-line" />}
            </div>
            <div className="agent-row-content">
              <div className="agent-row-topline">
                <div>
                  <strong>{task.title}</strong>
                  <span className="role-label">{task.role}{task.specialty ? ` / ${task.specialty}` : ''}</span>
                </div>
                <StatusBadge status={task.status} />
              </div>
              {task.resultSummary && <p>{task.resultSummary}</p>}
              {task.failure && <p className="failure-text">{task.failure}</p>}
              <div className="agent-row-footer">
                <time dateTime={task.updatedAt}>{formatTime(task.updatedAt)}</time>
                <span>Attempt {task.attempt}/{task.maxAttempts}</span>
                <span className="mono">{task.id.slice(0, 8)}</span>
              </div>
            </div>
          </article>
        ))}
        {tasks.length === 0 && <div className="empty-panel">No Agent tasks have been dispatched</div>}
      </div>
    </section>
  )
}
