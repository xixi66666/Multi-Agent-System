import {
  Activity,
  ArrowRight,
  ClipboardList,
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
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [details, setDetails] = useState<RunDetails | null>(null)
  const [tab, setTab] = useState<DetailTab>('flow')
  const [showCreateRun, setShowCreateRun] = useState(false)
  const [showRegisterProject, setShowRegisterProject] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const refreshTimer = useRef<number | null>(null)

  const loadRuns = useCallback(async (projectId: string | null = selectedProjectId) => {
    const nextRuns = await api.listRuns()
    setRuns(nextRuns)
    setSelectedId((current) => {
      if (current && nextRuns.some((run) => run.id === current)) return current
      const inProject = nextRuns.find((run) => (run.projectId ?? null) === projectId)
      return inProject?.id ?? nextRuns[0]?.id ?? null
    })
  }, [selectedProjectId])

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
      setSelectedProjectId((current) => current ?? firstProjectId(nextRuns, nextProjects))
      setSelectedId((current) => {
        if (current && nextRuns.some((run) => run.id === current)) return current
        const projectId = current ?? firstProjectId(nextRuns, nextProjects)
        const inProject = nextRuns.find((run) => (run.projectId ?? null) === projectId)
        return inProject?.id ?? nextRuns[0]?.id ?? null
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '无法加载调度台数据')
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
      setError(requestError instanceof Error ? requestError.message : '无法加载运行详情')
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
    setSelectedProjectId(projectId)
    setSelectedId(run.id)
    await loadRuns(projectId)
  }

  const registerProject = async (name: string, rootPath: string, type: Project['type']) => {
    await api.createProject(name, rootPath, type)
    const nextProjects = await api.listProjects()
    setProjects(nextProjects)
    setShowRegisterProject(false)
  }

  const selectProject = async (projectId: string | null) => {
    setSelectedProjectId(projectId)
    await loadRuns(projectId)
  }

  const deleteRun = async (runId: string) => {
    if (!window.confirm('确认删除该运行记录？其 git worktree 与分支将被一并清理。')) return
    setBusy(true)
    setError(null)
    try {
      await api.deleteRun(runId)
      await loadRuns()
      if (selectedId === runId) setDetails(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '运行删除失败')
    } finally {
      setBusy(false)
    }
  }

  const clearProject = async (projectId: string | null) => {
    if (!window.confirm(projectId ? '确认清空该项目的全部运行记录？其 worktree 与分支将一并清理。' : '确认清空未关联项目的全部运行记录？')) return
    setBusy(true)
    setError(null)
    try {
      await api.clearProjectRuns(projectId ?? '')
      await loadRuns()
      setDetails(null)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '批量清空失败')
    } finally {
      setBusy(false)
    }
  }

  const controlRun = async (action: 'pause' | 'resume' | 'cancel') => {
    if (!selectedId) return
    if (action === 'cancel' && !window.confirm('确认取消本次运行？已完成的工作区将被保留。')) return
    setBusy(true)
    setError(null)
    try {
      if (action === 'pause') await api.pauseRun(selectedId)
      if (action === 'resume') await api.resumeRun(selectedId)
      if (action === 'cancel') await api.cancelRun(selectedId)
      await Promise.all([loadRuns(), loadDetails(selectedId)])
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '运行控制操作失败')
    } finally {
      setBusy(false)
    }
  }

  const decideApproval = async (approvalId: string, decision: 'approve' | 'reject') => {
    if (!selectedId) return
    if (decision === 'approve' && !window.confirm('确认仅批准此一次外部操作？')) return
    setBusy(true)
    setError(null)
    try {
      await api.decideApproval(selectedId, approvalId, decision)
      await loadDetails(selectedId)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '审批操作失败')
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
      setError(requestError instanceof Error ? requestError.message : '无法创建 GitHub 推送审批')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="app-bar">
        <div className="brand-block">
          <div className="brand-mark"><GitFork size={21} aria-hidden="true" /></div>
          <div><strong>Vibe Agent</strong><span>本地多智能体调度台</span></div>
        </div>
        <div className="app-bar-actions">
          <span className="connection-state"><span /> 本地运行时已连接</span>
          <button type="button" className="button secondary" onClick={() => setShowRegisterProject(true)}>
            <FolderGit2 size={17} aria-hidden="true" /> 项目管理
          </button>
          <button type="button" className="button primary" onClick={() => setShowCreateRun(true)}>
            <Plus size={18} aria-hidden="true" /> 新建运行
          </button>
        </div>
      </header>

      <div className="app-layout">
        <RunList
          runs={runs}
          projects={projects}
          selectedProjectId={selectedProjectId}
          onSelectProject={(projectId) => void selectProject(projectId)}
          selectedRunId={selectedId}
          onSelectRun={setSelectedId}
          onDeleteRun={(runId) => void deleteRun(runId)}
          onClearProject={(projectId) => void clearProject(projectId)}
        />
        <main id="main-content" className="main-content">
          {error && <div className="global-error" role="alert">{error}</div>}
          {loading ? (
            <div className="loading-state"><span className="loading-ring" /><span>正在连接调度台</span></div>
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
              <nav className="detail-tabs" aria-label="运行详情">
                <button type="button" className={tab === 'flow' ? 'active' : ''} onClick={() => setTab('flow')}>
                  <LayoutDashboard size={17} aria-hidden="true" /> 任务流
                </button>
                <button type="button" className={tab === 'events' ? 'active' : ''} onClick={() => setTab('events')}>
                  <Radio size={17} aria-hidden="true" /> 事件 <span>{details.events.length}</span>
                </button>
                <button type="button" className={tab === 'usage' ? 'active' : ''} onClick={() => setTab('usage')}>
                  <Activity size={17} aria-hidden="true" /> 用量
                </button>
                <button type="button" className={tab === 'workspaces' ? 'active' : ''} onClick={() => setTab('workspaces')}>
                  <WalletCards size={17} aria-hidden="true" /> 工作区
                </button>
                <button type="button" className={tab === 'approvals' ? 'active' : ''} onClick={() => setTab('approvals')}>
                  <ShieldCheck size={17} aria-hidden="true" /> 审批 <span>{details.approvals.filter((item) => item.status === 'PENDING').length}</span>
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
              <section className="welcome-main" aria-labelledby="welcome-title">
                <div className="welcome-icon"><ServerCog size={27} aria-hidden="true" /></div>
                <p className="welcome-kicker">Vibe Agent 工作区</p>
                <h1 id="welcome-title">让每一次代码交付都有清晰的执行路径</h1>
                <p className="welcome-copy">从已授权的本地项目发起运行，任务、工作区、验证和审批会持续汇聚到这里。</p>
                <div className="welcome-actions">
                  <button type="button" className="button primary" onClick={() => setShowCreateRun(true)}>
                    <Plus size={18} aria-hidden="true" /> 新建运行
                  </button>
                  <button type="button" className="button secondary" onClick={() => setShowRegisterProject(true)}>
                    登记项目 <ArrowRight size={17} aria-hidden="true" />
                  </button>
                </div>
              </section>
              <section className="welcome-steps" aria-label="开始一次运行">
                <article>
                  <span className="step-number">01</span>
                  <FolderGit2 size={19} aria-hidden="true" />
                  <div><strong>登记项目</strong><p>选择一个已授权的本地 Git 仓库</p></div>
                </article>
                <article>
                  <span className="step-number">02</span>
                  <ClipboardList size={19} aria-hidden="true" />
                  <div><strong>提交需求</strong><p>定义本次需要交付的变更目标</p></div>
                </article>
                <article>
                  <span className="step-number">03</span>
                  <GitFork size={19} aria-hidden="true" />
                  <div><strong>跟踪执行</strong><p>查看任务流、验证结果与审批请求</p></div>
                </article>
              </section>
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

function firstProjectId(runs: RunSnapshot[], projects: Project[]): string | null {
  const withRuns = projects.find((project) => runs.some((run) => run.projectId === project.id))
  return withRuns?.id ?? projects[0]?.id ?? null
}
