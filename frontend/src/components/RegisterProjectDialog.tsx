import { FolderGit2 } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import type { Project } from '../types'
import { Modal } from './Modal'

interface RegisterProjectDialogProps {
  onClose: () => void
  onCreate: (name: string, rootPath: string, type: Project['type']) => Promise<void>
}

export function RegisterProjectDialog({ onClose, onCreate }: RegisterProjectDialogProps) {
  const [name, setName] = useState('')
  const [rootPath, setRootPath] = useState('')
  const [type, setType] = useState<Project['type']>('EXISTING_GIT')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await onCreate(name.trim(), rootPath.trim(), type)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Project could not be registered')
      setSubmitting(false)
    }
  }

  return (
    <Modal title="Register project" onClose={onClose}>
      <form className="modal-body" onSubmit={submit}>
        <div className="segmented" aria-label="Project type">
          <button type="button" className={type === 'EXISTING_GIT' ? 'active' : ''} onClick={() => setType('EXISTING_GIT')}>
            Existing Git
          </button>
          <button type="button" className={type === 'NEW_DIRECTORY' ? 'active' : ''} onClick={() => setType('NEW_DIRECTORY')}>
            New directory
          </button>
        </div>
        <label className="field">
          <span>Name</span>
          <input value={name} onChange={(event) => setName(event.target.value)} autoFocus />
        </label>
        <label className="field">
          <span>Local path</span>
          <input className="mono" value={rootPath} onChange={(event) => setRootPath(event.target.value)} placeholder="D:/Code/project" />
        </label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <footer className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="button primary" disabled={submitting || !name.trim() || !rootPath.trim()}>
            <FolderGit2 size={17} aria-hidden="true" />
            {submitting ? 'Saving...' : 'Register'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
