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
      setError(requestError instanceof Error ? requestError.message : 'Run could not be created')
      setSubmitting(false)
    }
  }

  return (
    <Modal title="New autonomous run" onClose={onClose}>
      <form className="modal-body" onSubmit={submit}>
        <label className="field">
          <span>Project</span>
          <select value={projectId} onChange={(event) => setProjectId(event.target.value)} disabled={projects.length === 0}>
            {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
          </select>
        </label>
        {projects.length === 0 && (
          <button type="button" className="inline-action" onClick={onRegisterProject}>
            <Plus size={16} aria-hidden="true" /> Register project
          </button>
        )}
        <label className="field">
          <span>Requirement</span>
          <textarea
            value={requirement}
            onChange={(event) => setRequirement(event.target.value)}
            rows={6}
            placeholder="Describe the change and acceptance outcome"
            autoFocus
          />
        </label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <footer className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="button primary" disabled={submitting || !projectId || !requirement.trim()}>
            <Play size={17} aria-hidden="true" />
            {submitting ? 'Starting...' : 'Start run'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
