export type RunStatus =
  | 'CREATED'
  | 'PLANNING'
  | 'IMPLEMENTING'
  | 'TESTING'
  | 'REVIEWING'
  | 'WAITING_FOR_INPUT'
  | 'WAITING_FOR_APPROVAL'
  | 'PAUSED'
  | 'NEEDS_ATTENTION'
  | 'COMPLETED'
  | 'COMPLETED_WITH_WARNINGS'
  | 'FAILED'
  | 'CANCELLED'

export interface AgentResult {
  role: string
  successful: boolean
  summary: string
  completedAt: string
}

export interface RunSnapshot {
  id: string
  projectId: string | null
  requirement: string
  workspace: string
  status: RunStatus
  results: Record<string, AgentResult>
  summary: string | null
  failure: string | null
  createdAt: string
  updatedAt: string
}

export interface Project {
  id: string
  name: string
  rootPath: string
  type: 'EXISTING_GIT' | 'NEW_DIRECTORY'
  createdAt: string
  updatedAt: string
}

export interface AgentTask {
  id: string
  runId: string
  parentTaskId: string | null
  role: string
  specialty: string | null
  title: string
  instructions: string | null
  status: string
  attempt: number
  maxAttempts: number
  leaseOwner: string | null
  leaseUntil: string | null
  resultSummary: string | null
  failure: string | null
  createdAt: string
  updatedAt: string
}

export interface RunEvent {
  id: number
  runId: string
  type: string
  payload: string
  createdAt: string
}

export interface ModelUsage {
  id: string
  role: string
  provider: string
  model: string
  inputTokens: number
  outputTokens: number
  reasoningTokens: number
  cachedInputTokens: number
  totalTokens: number
  estimatedCost: number
  estimated: boolean
  latencyMillis: number
  requestStatus: string
  failureType: string | null
  createdAt: string
}

export interface ModelUsageSummary {
  runId: string
  calls: number
  inputTokens: number
  outputTokens: number
  reasoningTokens: number
  cachedInputTokens: number
  totalTokens: number
  estimatedCost: number
  usages: ModelUsage[]
}

export interface TaskWorkspace {
  id: string
  runId: string
  taskId: string | null
  type: 'INTEGRATION' | 'TASK'
  path: string
  branchName: string
  baseRevision: string
  createdAt: string
}

export interface Approval {
  id: string
  runId: string
  taskId: string | null
  actionType: string
  requestPayload: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'FAILED'
  decisionNote: string | null
  requestedAt: string
  decidedAt: string | null
}

export interface RunDetails {
  run: RunSnapshot
  tasks: AgentTask[]
  events: RunEvent[]
  usage: ModelUsageSummary
  workspaces: TaskWorkspace[]
  approvals: Approval[]
}
