import { CircleGauge, Coins, DatabaseZap, Timer } from 'lucide-react'
import { formatNumber, formatTime } from '../format'
import type { ModelUsageSummary } from '../types'
import { StatusBadge } from './StatusBadge'

export function UsagePanel({ usage }: { usage: ModelUsageSummary }) {
  const roleTotals = Object.entries(usage.usages.reduce<Record<string, number>>((totals, item) => {
    totals[item.role] = (totals[item.role] ?? 0) + item.totalTokens
    return totals
  }, {})).sort((a, b) => b[1] - a[1])
  const max = Math.max(...roleTotals.map(([, total]) => total), 1)

  return (
    <section className="usage-panel" aria-label="Model usage">
      <header className="panel-heading">
        <div>
          <span className="section-label">Model telemetry</span>
          <h2>Token usage</h2>
        </div>
      </header>
      <div className="usage-summary">
        <div><DatabaseZap size={18} aria-hidden="true" /><span>Total tokens</span><strong>{formatNumber(usage.totalTokens)}</strong></div>
        <div><CircleGauge size={18} aria-hidden="true" /><span>Calls</span><strong>{usage.calls}</strong></div>
        <div><Timer size={18} aria-hidden="true" /><span>Reasoning</span><strong>{formatNumber(usage.reasoningTokens)}</strong></div>
        <div><Coins size={18} aria-hidden="true" /><span>Estimated cost</span><strong>${Number(usage.estimatedCost).toFixed(4)}</strong></div>
      </div>
      <div className="role-bars" aria-label="Tokens by Agent role">
        {roleTotals.map(([role, total]) => (
          <div className="role-bar-row" key={role}>
            <span>{role}</span>
            <div className="role-bar-track"><span style={{ width: `${Math.max(4, total / max * 100)}%` }} /></div>
            <strong className="tabular">{formatNumber(total)}</strong>
          </div>
        ))}
      </div>
      <div className="data-table-wrap">
        <table className="data-table">
          <thead><tr><th>Time</th><th>Agent</th><th>Provider / model</th><th>Status</th><th>Input</th><th>Output</th><th>Total</th><th>Latency</th></tr></thead>
          <tbody>
            {[...usage.usages].reverse().map((item) => (
              <tr key={item.id}>
                <td className="tabular">{formatTime(item.createdAt)}</td>
                <td>{item.role}</td>
                <td><strong>{item.provider}</strong><small>{item.model}</small></td>
                <td><StatusBadge status={item.requestStatus} /></td>
                <td className="tabular">{formatNumber(item.inputTokens)}</td>
                <td className="tabular">{formatNumber(item.outputTokens)}</td>
                <td className="tabular">{formatNumber(item.totalTokens)}{item.estimated ? <sup title="Estimated">~</sup> : null}</td>
                <td className="tabular">{formatNumber(item.latencyMillis)}ms</td>
              </tr>
            ))}
          </tbody>
        </table>
        {usage.usages.length === 0 && <div className="empty-panel">No model calls recorded</div>}
      </div>
    </section>
  )
}
