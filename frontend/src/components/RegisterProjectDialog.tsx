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
      setError(requestError instanceof Error ? requestError.message : '无法登记项目')
      setSubmitting(false)
    }
  }

  return (
    <Modal title="登记项目" onClose={onClose}>
      <form className="modal-body" onSubmit={submit}>
        <div className="segmented" aria-label="项目类型">
          <button type="button" className={type === 'EXISTING_GIT' ? 'active' : ''} onClick={() => setType('EXISTING_GIT')}>
            现有 Git 项目
          </button>
          <button type="button" className={type === 'NEW_DIRECTORY' ? 'active' : ''} onClick={() => setType('NEW_DIRECTORY')}>
            新建目录
          </button>
        </div>
        <label className="field">
          <span>项目名称</span>
          <input value={name} onChange={(event) => setName(event.target.value)} autoFocus />
        </label>
        <label className="field">
          <span>本地路径</span>
          <input className="mono" value={rootPath} onChange={(event) => setRootPath(event.target.value)} placeholder="D:/Code/project" />
        </label>
        {error && <div className="form-error" role="alert">{error}</div>}
        <footer className="modal-actions">
          <button type="button" className="button secondary" onClick={onClose}>取消</button>
          <button type="submit" className="button primary" disabled={submitting || !name.trim() || !rootPath.trim()}>
            <FolderGit2 size={17} aria-hidden="true" />
            {submitting ? '正在保存...' : '登记项目'}
          </button>
        </footer>
      </form>
    </Modal>
  )
}
