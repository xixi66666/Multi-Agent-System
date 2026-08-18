import { Activity, Bot, CircleDot, Cpu, Wrench } from 'lucide-react'
import { formatTime } from '../format'
import type { RunEvent } from '../types'

function eventIcon(type: string) {
  if (type.startsWith('agent.')) return <Bot size={16} aria-hidden="true" />
  if (type.startsWith('model.')) return <Cpu size={16} aria-hidden="true" />
  if (type.startsWith('tool.')) return <Wrench size={16} aria-hidden="true" />
  if (type.startsWith('run.')) return <Activity size={16} aria-hidden="true" />
  return <CircleDot size={16} aria-hidden="true" />
}

function eventSummary(event: RunEvent) {
  try {
    const payload = JSON.parse(event.payload) as Record<string, unknown>
    return Object.entries(payload)
      .filter(([key]) => !['taskId', 'workspaceId'].includes(key))
      .slice(0, 4)
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join('  ·  ')
  } catch {
    return event.payload
  }
}

export function EventTimeline({ events }: { events: RunEvent[] }) {
  const ordered = [...events].reverse()
  return (
    <section className="events-panel" aria-label="运行事件时间线">
      <header className="panel-heading">
        <div>
          <span className="section-label">实时流</span>
          <h2>事件时间线</h2>
        </div>
        <span className="live-indicator"><span /> 实时更新</span>
      </header>
      <div className="event-list">
        {ordered.map((event) => (
          <article className="event-row" key={event.id}>
            <div className="event-icon">{eventIcon(event.type)}</div>
            <div>
              <div className="event-heading">
                <strong>{event.type}</strong>
                <time dateTime={event.createdAt}>{formatTime(event.createdAt)}</time>
              </div>
              <p>{eventSummary(event)}</p>
            </div>
          </article>
        ))}
        {events.length === 0 && <div className="empty-panel">暂未记录运行事件</div>}
      </div>
    </section>
  )
}
