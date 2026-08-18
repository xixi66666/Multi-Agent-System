import { Play, Plus } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import type { Project } from '../types'
import { Modal } from './Modal'

interface CreateRunDialogProps {
  projects: Project[]
  onClose: () => void
  onCreate: (requirement: string, projectId: string) => Promise<void>
  onRegisterProject: () => void
}

export function CreateRunDialog({ projects, onClose, onCreate, onRegisterProject }: CreateRunDialogProps) {
  const [requirement, setRequirement] = useState('')
  const [projectId, setProjectId] = useState(projects[0]?.id ?? '')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!requirement.trim() || !projectId) return
    setSubmitting(true)
    setError(null)
    try {
      await onCreate(requirement.trim(), projectId)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '无法创建运行')
      setSubmitting(false)
    }
  }

  return (
    <Modal title="新建自主运行" onClose={onClose}>
      <form className="modal-body" onSubmit={submit}>
        <label className="field">
          <span>目标项目</span>
          <select value={projectId} onChange={(event) => setProjectId(event.target.value)} disabled={projects.length === 0}>
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select>
        </label>
        {projects.length === 0 && (
          <button type="button" className="inline-action" onClick={onRegisterProject}>
            <Plus size={16} aria-hidden="true" /> 先登记项目
          </button>
        )}
        <label className="field">
          <span>需求描述</span>
          <textarea
            value={requirement}
            onChange={(event) => setRequirement(event.target.value)}
            rows={6}
            placeholder="描述需要完成的改动、约束条件和验收结果"
            autoFocus
          />
        </label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <footer className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>取消</button>
          <button type="submit" className="button primary" disabled={submitting || !projectId || !requirement.trim()}>
            <Play size={17} aria-hidden="true" />
            {submitting ? '正在启动...' : '启动运行'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
