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
    <section className="flow-panel" aria-label="智能体执行流程">
      <header className="panel-heading">
        <div>
          <span className="section-label">执行编排</span>
          <h2>智能体任务流</h2>
        </div>
        <span className="panel-count">{tasks.length} 个任务</span>
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
                <span>第 {task.attempt}/{task.maxAttempts} 次尝试</span>
                <span className="mono">{task.id.slice(0, 8)}</span>
              </div>
            </div>
          </article>
        ))}
        {tasks.length === 0 && <div className="empty-panel">暂未分发智能体任务</div>}
      </div>
    </section>
  )
}
