import {
  Activity,
  FolderGit2,
  GitFork,
  LayoutDashboard,
  Plus,
  Radio,
  ServerCog,
  ShieldCheck,
  WalletCards,
} from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './api'
import { AgentFlow } from './components/AgentFlow'
import { ApprovalsPanel } from './components/ApprovalsPanel'
import { CreateRunDialog } from './components/CreateRunDialog'
import { EventTimeline } from './components/EventTimeline'
import { MetricsStrip } from './components/MetricsStrip'
import { RegisterProjectDialog } from './components/RegisterProjectDialog'
import { RunHeader } from './components/RunHeader'
import { RunList } from './components/RunList'
import { UsagePanel } from './components/UsagePanel'
import { WorkspacePanel } from './components/WorkspacePanel'
import { useRunStream } from './hooks/useRunStream'
import type { Project, RunDetails, RunEvent, RunSnapshot } from './types'

type DetailTab = 'flow' | 'events' | 'usage' | 'workspaces' | 'approvals'

export function App() {
  const [runs, setRuns] = useState<RunSnapshot[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [details, setDetails] = useState<RunDetails | null>(null)
  const [tab, setTab] = useState<DetailTab>('flow')
  const [showCreateRun, setShowCreateRun] = useState(false)
  const [showRegisterProject, setShowRegisterProject] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const refreshTimer = useRef<number | null>(null)

  const loadRuns = useCallback(async () => {
    const nextRuns = await api.listRuns()
    setRuns(nextRuns)
    setSelectedId((current) => current ?? nextRuns[0]?.id ?? null)
  }, [])

  const loadDetails = useCallback(async (id: string) => {
    const nextDetails = await api.getDetails(id)
    setDetails(nextDetails)
  }, [])

  const loadInitial = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [nextRuns, nextProjects] = await Promise.all([api.listRuns(), api.listProjects()])
      setRuns(nextRuns)
      setProjects(nextProjects)
      setSelectedId((current) => current ?? nextRuns[0]?.id ?? null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Console data could not be loaded')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadInitial()
  }, [loadInitial])

  useEffect(() => {
    if (!selectedId) {
      setDetails(null)
      return
    }
    setError(null)
    void loadDetails(selectedId).catch((requestError) => {
      setError(requestError instanceof Error ? requestError.message : 'Run details could not be loaded')
    })
  }, [loadDetails, selectedId])

  const handleRunEvent = useCallback((event: RunEvent) => {
    setDetails((current) => {
      if (!current || current.run.id !== event.runId || current.events.some((item) => item.id === event.id)) return current
      return { ...current, events: [...current.events, event] }
    })
    if (refreshTimer.current !== null) window.clearTimeout(refreshTimer.current)
    refreshTimer.current = window.setTimeout(() => {
      void Promise.all([loadRuns(), loadDetails(event.runId)]).catch(() => undefined)
    }, 140)
  }, [loadDetails, loadRuns])

  useRunStream(selectedId, handleRunEvent)

  const createRun = async (requirement: string, projectId: string) => {
    const run = await api.createRun(requirement, projectId)
    setShowCreateRun(false)
    setSelectedId(run.id)
    await loadRuns()
  }

  const registerProject = async (name: string, rootPath: string, type: Project['type']) => {
    await api.createProject(name, rootPath, type)
    setProjects(await api.listProjects())
    setShowRegisterProject(false)
  }

  const controlRun = async (action: 'pause' | 'resume' | 'cancel') => {
    if (!selectedId) return
    if (action === 'cancel' && !window.confirm('Cancel this run? Completed worktrees will be preserved.')) return
    setBusy(true)
    setError(null)
    try {
      if (action === 'pause') await api.pauseRun(selectedId)
      if (action === 'resume') await api.resumeRun(selectedId)
      if (action === 'cancel') await api.cancelRun(selectedId)
      await Promise.all([loadRuns(), loadDetails(selectedId)])
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Run control action failed')
    } finally {
      setBusy(false)
    }
  }

  const decideApproval = async (approvalId: string, decision: 'approve' | 'reject') => {
    if (!selectedId) return
    if (decision === 'approve' && !window.confirm('Approve this external action once?')) return
    setBusy(true)
    setError(null)
    try {
      await api.decideApproval(selectedId, approvalId, decision)
      await loadDetails(selectedId)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Approval decision failed')
    } finally {
      setBusy(false)
    }
  }

  const prepareGitHubPush = async () => {
    if (!selectedId) return
    setBusy(true)
    setError(null)
    try {
      await api.prepareGitHubPush(selectedId)
      await loadDetails(selectedId)
      setTab('approvals')
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'GitHub push approval could not be prepared')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="app-bar">
        <div className="brand-block">
          <div className="brand-mark"><GitFork size={21} aria-hidden="true" /></div>
          <div><strong>Vibe Agent</strong><span>Local orchestration console</span></div>
        </div>
        <div className="app-bar-actions">
          <span className="connection-state"><span /> Local runtime</span>
          <button type="button" className="button secondary" onClick={() => setShowRegisterProject(true)}>
            <FolderGit2 size={17} aria-hidden="true" /> Projects
          </button>
          <button type="button" className="button primary" onClick={() => setShowCreateRun(true)}>
            <Plus size={18} aria-hidden="true" /> New run
          </button>
        </div>
      </header>

      <div className="app-layout">
        <RunList runs={runs} selectedId={selectedId} onSelect={setSelectedId} />
        <main id="main-content" className="main-content">
          {error && <div className="global-error" role="alert">{error}</div>}
          {loading ? (
            <div className="loading-state"><span className="loading-ring" /><span>Loading console</span></div>
          ) : details ? (
            <>
              <RunHeader
                run={details.run}
                busy={busy}
                onPause={() => void controlRun('pause')}
                onResume={() => void controlRun('resume')}
                onCancel={() => void controlRun('cancel')}
                onRefresh={() => void Promise.all([loadRuns(), loadDetails(details.run.id)])}
                onPrepareGitHub={() => void prepareGitHubPush()}
              />
              <MetricsStrip details={details} />
              <nav className="detail-tabs" aria-label="Run details">
                <button type="button" className={tab === 'flow' ? 'active' : ''} onClick={() => setTab('flow')}>
                  <LayoutDashboard size={17} aria-hidden="true" /> Flow
                </button>
                <button type="button" className={tab === 'events' ? 'active' : ''} onClick={() => setTab('events')}>
                  <Radio size={17} aria-hidden="true" /> Events <span>{details.events.length}</span>
                </button>
                <button type="button" className={tab === 'usage' ? 'active' : ''} onClick={() => setTab('usage')}>
                  <Activity size={17} aria-hidden="true" /> Usage
                </button>
                <button type="button" className={tab === 'workspaces' ? 'active' : ''} onClick={() => setTab('workspaces')}>
                  <WalletCards size={17} aria-hidden="true" /> Workspaces
                </button>
                <button type="button" className={tab === 'approvals' ? 'active' : ''} onClick={() => setTab('approvals')}>
                  <ShieldCheck size={17} aria-hidden="true" /> Approvals <span>{details.approvals.filter((item) => item.status === 'PENDING').length}</span>
                </button>
              </nav>
              <div className="detail-content">
                {tab === 'flow' && <div className="flow-layout"><AgentFlow tasks={details.tasks} /><EventTimeline events={details.events.slice(-12)} /></div>}
                {tab === 'events' && <EventTimeline events={details.events} />}
                {tab === 'usage' && <UsagePanel usage={details.usage} />}
                {tab === 'workspaces' && <WorkspacePanel workspaces={details.workspaces} />}
                {tab === 'approvals' && <ApprovalsPanel approvals={details.approvals} busy={busy} onDecision={(id, decision) => void decideApproval(id, decision)} />}
              </div>
            </>
          ) : (
            <div className="welcome-state">
              <div className="welcome-icon"><ServerCog size={27} aria-hidden="true" /></div>
              <h1>No run selected</h1>
              <button type="button" className="button primary" onClick={() => setShowCreateRun(true)}>
                <Plus size={18} aria-hidden="true" /> New run
              </button>
            </div>
          )}
        </main>
      </div>

      {showCreateRun && (
        <CreateRunDialog
          projects={projects}
          onClose={() => setShowCreateRun(false)}
          onCreate={createRun}
          onRegisterProject={() => {
            setShowCreateRun(false)
            setShowRegisterProject(true)
          }}
        />
      )}
      {showRegisterProject && (
        <RegisterProjectDialog onClose={() => setShowRegisterProject(false)} onCreate={registerProject} />
      )}
    </div>
  )
}
