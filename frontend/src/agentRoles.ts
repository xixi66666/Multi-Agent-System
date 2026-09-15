export interface AgentRoleMeta {
  label: string
  shortLabel: string
  phase: string
  description: string
  deliverable: string
  tone: string
}

const ROLE_CATALOG: Record<string, AgentRoleMeta> = {
  PLANNER: {
    label: '规划智能体',
    shortLabel: '规划',
    phase: '需求拆解',
    description: '理解完整需求与仓库上下文，把目标拆成边界清晰、可以验证的执行计划。',
    deliverable: '执行计划与验收标准',
    tone: 'planner',
  },
  RESEARCHER: {
    label: '调研智能体',
    shortLabel: '调研',
    phase: '资料调研',
    description: '以只读方式检索仓库和可信文档，为实现补齐事实、限制条件与技术依据。',
    deliverable: '调研结论与参考依据',
    tone: 'researcher',
  },
  ARCHITECT: {
    label: '架构智能体',
    shortLabel: '架构',
    phase: '方案设计',
    description: '定义模块边界、接口与数据契约，提前明确影响实现的关键架构决策。',
    deliverable: '架构方案与接口契约',
    tone: 'architect',
  },
  IMPLEMENTER: {
    label: '开发智能体',
    shortLabel: '开发',
    phase: '代码实现',
    description: '在独立工作区完成分配的编码任务，遵循项目规范并记录改动与风险。',
    deliverable: '可集成的代码变更',
    tone: 'implementer',
  },
  INTEGRATOR: {
    label: '集成智能体',
    shortLabel: '集成',
    phase: '成果集成',
    description: '汇总各开发分支，处理确定性的冲突，并保护每项任务留下的交付证据。',
    deliverable: '集成结果与冲突报告',
    tone: 'integrator',
  },
  TESTER: {
    label: '测试智能体',
    shortLabel: '测试',
    phase: '质量验证',
    description: '依据验收标准执行构建与测试，如实报告命令、证据、失败和残余风险。',
    deliverable: '测试证据与风险清单',
    tone: 'tester',
  },
  REVIEWER: {
    label: '审查智能体',
    shortLabel: '审查',
    phase: '最终审查',
    description: '从需求、安全边界、测试覆盖和无关改动等角度检查最终交付质量。',
    deliverable: '审查结论与修复意见',
    tone: 'reviewer',
  },
  FRONTEND: {
    label: '前端智能体',
    shortLabel: '前端',
    phase: '界面实现',
    description: '负责页面结构、交互体验与前端接口接入，确保界面可用且易于维护。',
    deliverable: '前端代码与交互说明',
    tone: 'frontend',
  },
  BACKEND: {
    label: '后端智能体',
    shortLabel: '后端',
    phase: '服务实现',
    description: '负责业务逻辑、接口和数据访问实现，保证服务端行为稳定且边界清晰。',
    deliverable: '服务端代码与接口结果',
    tone: 'backend',
  },
  TEST: {
    label: '验证智能体',
    shortLabel: '验证',
    phase: '联合验证',
    description: '对前后端协作结果执行验证，确认核心流程和既有功能没有被破坏。',
    deliverable: '联合验证结果',
    tone: 'test',
  },
}

const UNKNOWN_ROLE: AgentRoleMeta = {
  label: '协作智能体',
  shortLabel: '协作',
  phase: '专项协作',
  description: '根据调度器分配的上下文执行专项任务，并向后续角色移交结果。',
  deliverable: '专项任务结果',
  tone: 'default',
}

const SPECIALTY_LABELS: Record<string, string> = {
  requirements: '需求分析',
  documentation: '文档与资料',
  'system-design': '系统设计',
  'git-integration': '代码集成',
  verification: '验收验证',
  'review-repair': '审查修复',
  'repair-verification': '修复复验',
  frontend: '前端开发',
  backend: '后端开发',
  database: '数据库',
  testing: '测试',
}

export const AUTONOMOUS_ROLE_ORDER = [
  'PLANNER',
  'RESEARCHER',
  'ARCHITECT',
  'IMPLEMENTER',
  'INTEGRATOR',
  'TESTER',
  'REVIEWER',
] as const

export function agentRoleMeta(role: string): AgentRoleMeta {
  return ROLE_CATALOG[role] ?? {
    ...UNKNOWN_ROLE,
    label: `${UNKNOWN_ROLE.label}（${role}）`,
  }
}

export function formatAgentRole(role: string): string {
  return agentRoleMeta(role).label
}

export function formatSpecialty(specialty: string | null): string | null {
  if (!specialty) return null
  return SPECIALTY_LABELS[specialty] ?? specialty.replaceAll('-', ' ')
}
