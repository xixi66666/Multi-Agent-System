import { Eraser, FolderGit2, FolderOpen, Search, Trash2, Workflow } from 'lucide-react'
import { useMemo, useState } from 'react'
import { formatDateTime, shortId } from '../format'
import type { Project, RunSnapshot } from '../types'
import { StatusBadge } from './StatusBadge'

interface RunListProps {
  runs: RunSnapshot[]
  projects: Project[]
  selectedProjectId: string | null
  onSelectProject: (projectId: string | null) => void
  selectedRunId: string | null
  onSelectRun: (id: string) => void
  onDeleteRun: (id: string) => void
  onClearProject: (projectId: string | null) => void
}

interface ProjectEntry {
  id: string | null
  label: string
  count: number
}

export function RunList({
  runs,
  projects,
  selectedProjectId,
  onSelectProject,
  selectedRunId,
  onSelectRun,
  onDeleteRun,
  onClearProject,
}: RunListProps) {
  const [query, setQuery] = useState('')

  const projectEntries = useMemo(() => {
    const counts = new Map<string, number>()
    let unassigned = 0
    for (const run of runs) {
      if (run.projectId) counts.set(run.projectId, (counts.get(run.projectId) ?? 0) + 1)
      else unassigned++
    }
    const entries: ProjectEntry[] = projects
      .filter((project) => (counts.get(project.id) ?? 0) > 0)
      .map((project) => ({ id: project.id, label: project.name, count: counts.get(project.id) ?? 0 }))
      .sort((a, b) => a.label.localeCompare(b.label))
    if (unassigned > 0) entries.push({ id: null, label: '未关联项目', count: unassigned })
    return entries
  }, [runs, projects])

  const runsOfProject = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return runs
      .filter((run) => (selectedProjectId ?? '') === (run.projectId ?? ''))
      .filter((run) =>
        !normalized
        || run.requirement.toLowerCase().includes(normalized)
        || run.status.toLowerCase().includes(normalized)
        || run.id.toLowerCase().includes(normalized),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [runs, selectedProjectId, query])

  const selectedProject = selectedProjectId ? projects.find((project) => project.id === selectedProjectId) : undefined

  return (
    <aside className="run-sidebar" aria-label="运行队列">
      <div className="sidebar-heading">
        <div>
          <span className="section-label">运行队列</span>
          <strong>{runs.length} 次运行 · {projectEntries.length} 个项目</strong>
        </div>
      </div>
      <div className="project-shelf" aria-label="项目列表">
        {projectEntries.map((entry) => (
          <button
            type="button"
            key={entry.id ?? '__unassigned__'}
            className={`project-item ${selectedProjectId === entry.id ? 'selected' : ''}`}
            onClick={() => onSelectProject(entry.id)}
            aria-current={selectedProjectId === entry.id ? 'true' : undefined}
          >
            {selectedProjectId === entry.id ? <FolderOpen size={15} aria-hidden="true" /> : <FolderGit2 size={15} aria-hidden="true" />}
            <span>{entry.label}</span>
            <small>{entry.count}</small>
          </button>
        ))}
        {projectEntries.length === 0 && <div className="empty-compact">还没有项目</div>}
      </div>
      <div className="run-project-heading">
        <span className="section-label">{selectedProject ? selectedProject.name : '未关联项目'}</span>
        {selectedProjectId && runsOfProject.length > 0 && (
          <button type="button" className="text-button danger" onClick={() => onClearProject(selectedProjectId)} title="清空该项目全部运行">
            <Eraser size={13} aria-hidden="true" /> 清空
          </button>
        )}
      </div>
      <label className="search-field">
        <span className="sr-only">筛选运行</span>
        <Search size={16} aria-hidden="true" />
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索需求、状态或运行编号" />
      </label>
      <div className="run-list">
        {runsOfProject.map((run) => (
          <div key={run.id} className={`run-list-item-wrap ${selectedRunId === run.id ? 'selected' : ''}`}>
            <button
              type="button"
              className="run-list-item"
              onClick={() => onSelectRun(run.id)}
              aria-current={selectedRunId === run.id ? 'true' : undefined}
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
            <button
              type="button"
              className="run-delete"
              onClick={() => onDeleteRun(run.id)}
              aria-label={`删除运行 ${shortId(run.id)}`}
              title="删除该运行及 worktree"
            >
              <Trash2 size={15} aria-hidden="true" />
            </button>
          </div>
        ))}
        {runsOfProject.length === 0 && <div className="empty-compact">没有运行记录</div>}
      </div>
    </aside>
  )
}
