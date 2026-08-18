import { Bot, Clock3, Cpu, Sigma } from 'lucide-react'
import { elapsed, formatNumber } from '../format'
import type { RunDetails } from '../types'

export function MetricsStrip({ details }: { details: RunDetails }) {
  const active = details.tasks.filter((task) => task.status === 'RUNNING').length
  const completed = details.tasks.filter((task) => task.status === 'COMPLETED').length
  const providers = new Set(details.usage.usages.map((usage) => usage.provider)).size
  const terminal = ['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED', 'CANCELLED'].includes(details.run.status)
  return (
  <section className="metrics-strip" aria-label="运行指标">
      <div className="metric">
        <Bot size={18} aria-hidden="true" />
        <span>智能体</span>
        <strong>{active} 个活跃 <small>/ {completed} 个完成</small></strong>
      </div>
      <div className="metric">
        <Sigma size={18} aria-hidden="true" />
        <span>令牌总量</span>
        <strong className="tabular">{formatNumber(details.usage.totalTokens)}</strong>
      </div>
      <div className="metric">
        <Cpu size={18} aria-hidden="true" />
        <span>模型调用</span>
        <strong className="tabular">{details.usage.calls} <small>/ {providers} 个服务商</small></strong>
      </div>
      <div className="metric">
        <Clock3 size={18} aria-hidden="true" />
        <span>已耗时</span>
        <strong className="tabular">{elapsed(details.run.createdAt, terminal ? details.run.updatedAt : undefined)}</strong>
      </div>
    </section>
  )
}
