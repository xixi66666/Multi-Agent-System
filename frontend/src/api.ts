import type {
  Project,
  RunDetails,
  RunEvent,
  RunSnapshot,
  AgentTask,
  ModelUsageSummary,
  TaskWorkspace,
  Approval,
} from './types'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { error?: string } | null
    throw new Error(body?.error ?? `请求失败（状态码 ${response.status}）`)
  }
  if (response.status === 204) {
    return undefined as unknown as T
  }
  return response.json() as Promise<T>
}

export const api = {
  listRuns: () => request<RunSnapshot[]>('/api/runs'),
  listProjects: () => request<Project[]>('/api/projects'),
  getRun: (id: string) => request<RunSnapshot>(`/api/runs/${id}`),
  getTasks: (id: string) => request<AgentTask[]>(`/api/runs/${id}/tasks`),
  getEvents: (id: string) => request<RunEvent[]>(`/api/runs/${id}/events`),
  getUsage: (id: string) => request<ModelUsageSummary>(`/api/runs/${id}/usage`),
  getWorkspaces: (id: string) => request<TaskWorkspace[]>(`/api/runs/${id}/workspaces`),
  getApprovals: (id: string) => request<Approval[]>(`/api/runs/${id}/approvals`),
  getDetails: async (id: string): Promise<RunDetails> => {
    const [run, tasks, events, usage, workspaces, approvals] = await Promise.all([
      api.getRun(id),
      api.getTasks(id),
      api.getEvents(id),
      api.getUsage(id),
      api.getWorkspaces(id),
      api.getApprovals(id),
    ])
    return { run, tasks, events, usage, workspaces, approvals }
  },
  createRun: (requirement: string, projectId: string) =>
    request<RunSnapshot>('/api/runs', {
      method: 'POST',
      body: JSON.stringify({ requirement, projectId }),
    }),
  createProject: (name: string, rootPath: string, type: Project['type']) =>
    request<Project>('/api/projects', {
      method: 'POST',
      body: JSON.stringify({ name, rootPath, type }),
    }),
  pauseRun: (id: string) => request<RunSnapshot>(`/api/runs/${id}/pause`, { method: 'POST' }),
  resumeRun: (id: string) => request<RunSnapshot>(`/api/runs/${id}/resume`, { method: 'POST' }),
  cancelRun: (id: string) => request<RunSnapshot>(`/api/runs/${id}/cancel`, { method: 'POST' }),
  decideApproval: (runId: string, approvalId: string, decision: 'approve' | 'reject') =>
    request<Approval>(`/api/runs/${runId}/approvals/${approvalId}/${decision}`, {
      method: 'POST',
      body: JSON.stringify({}),
    }),
  prepareGitHubPush: (runId: string) =>
    request<Approval>(`/api/runs/${runId}/approvals/github-push`, { method: 'POST' }),
  deleteRun: (runId: string) => request<void>(`/api/runs/${runId}`, { method: 'DELETE' }),
  clearProjectRuns: (projectId: string) =>
    request<void>(`/api/runs?projectId=${encodeURIComponent(projectId)}`, { method: 'DELETE' }),
}
