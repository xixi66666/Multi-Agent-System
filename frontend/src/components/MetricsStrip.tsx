import { Bot, Clock3, Cpu, Sigma } from 'lucide-react'
import { elapsed, formatNumber } from '../format'
import type { RunDetails } from '../types'

export function MetricsStrip({ details }: { details: RunDetails }) {
  const active = details.tasks.filter((task) => task.status === 'RUNNING').length
  const completed = details.tasks.filter((task) => task.status === 'COMPLETED').length
  const providers = new Set(details.usage.usages.map((usage) => usage.provider)).size
  return (
    <section className="metrics-strip" aria-label="Run metrics">
      <div className="metric">
        <Bot size={18} aria-hidden="true" />
        <span>Agents</span>
        <strong>{active} active <small>/ {completed} done</small></strong>
      </div>
      <div className="metric">
        <Sigma size={18} aria-hidden="true" />
        <span>Tokens</span>
        <strong className="tabular">{formatNumber(details.usage.totalTokens)}</strong>
      </div>
      <div className="metric">
        <Cpu size={18} aria-hidden="true" />
        <span>Model calls</span>
        <strong className="tabular">{details.usage.calls} <small>/ {providers} providers</small></strong>
      </div>
      <div className="metric">
        <Clock3 size={18} aria-hidden="true" />
        <span>Elapsed</span>
        <strong className="tabular">{elapsed(details.run.createdAt, details.run.status.includes('COMPLETED') ? details.run.updatedAt : undefined)}</strong>
      </div>
    </section>
  )
}
