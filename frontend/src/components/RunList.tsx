import { Search, Workflow } from 'lucide-react'
import { useMemo, useState } from 'react'
import { formatDateTime, shortId } from '../format'
import type { RunSnapshot } from '../types'
import { StatusBadge } from './StatusBadge'

interface RunListProps {
  runs: RunSnapshot[]
  selectedId: string | null
  onSelect: (id: string) => void
}

export function RunList({ runs, selectedId, onSelect }: RunListProps) {
  const [query, setQuery] = useState('')
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return runs
    return runs.filter((run) =>
      run.requirement.toLowerCase().includes(normalized)
      || run.status.toLowerCase().includes(normalized)
      || run.id.toLowerCase().includes(normalized),
    )
  }, [query, runs])

  return (
    <aside className="run-sidebar" aria-label="Runs">
      <div className="sidebar-heading">
        <div>
          <span className="section-label">Queue</span>
          <strong>{runs.length} runs</strong>
        </div>
      </div>
      <label className="search-field">
        <span className="sr-only">Filter runs</span>
        <Search size={16} aria-hidden="true" />
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Filter runs" />
      </label>
      <div className="run-list">
        {filtered.map((run) => (
          <button
            type="button"
            className={`run-list-item ${selectedId === run.id ? 'selected' : ''}`}
            key={run.id}
            onClick={() => onSelect(run.id)}
            aria-current={selectedId === run.id ? 'true' : undefined}
          >
            <div className="run-list-title">
              <Workflow size={16} aria-hidden="true" />
              <span>{run.requirement}</span>
            </div>
            <div className="run-list-meta">
              <StatusBadge status={run.status} />
              <span className="mono">{shortId(run.id)}</span>
              <time dateTime={run.createdAt}>{formatDateTime(run.createdAt)}</time>
            </div>
          </button>
        ))}
        {filtered.length === 0 && <div className="empty-compact">No matching runs</div>}
      </div>
    </aside>
  )
}
