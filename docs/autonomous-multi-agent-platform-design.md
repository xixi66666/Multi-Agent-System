# 自治多 Agent 平台设计与实施说明

## 1. 目标和边界

本项目的目标是一个本地单机运行的自治软件开发平台。用户提交需求后，平台可以自主完成需求分析、项目理解、任务拆分、代码修改、测试验证、失败修复、审查和最终报告。

平台的自治边界是：在本机任务 worktree 内产生经过验证的改动。平台可以准备本地提交、分支和 PR 内容；`git push`、创建或更新 GitHub PR、评论等外部写操作必须等待用户批准。

首期不实现多用户、组织、租户、自动部署、生产环境变更和不受限制的 Shell/文件系统访问。

## 2. 已确认的设计决策

| 主题 | 决策 |
| --- | --- |
| 运行模式 | 本地单机，后端和控制台默认只监听 `127.0.0.1` |
| 任务存储 | MySQL + Flyway；当前迁移兼容并已验证 MySQL 5.7.44，长期建议 MySQL 8.0+ |
| 调度 | 数据库持久事件与 outbox，首期使用进程内调度器，不引入消息队列 |
| Agent 团队 | 注册角色固定，具体任务团队由规划动态组建 |
| 共享上下文 | 结构化共享工作台、定向消息和按需检索；不默认共享完整对话 |
| 项目来源 | 已有 Git 项目，或受控父目录中的新建空白项目 |
| 代码隔离 | 任务集成 worktree 加上每个可写子任务的独立 worktree/分支 |
| 模型 | `mimo`、`deepseek` 通过 OpenAI-compatible API；按角色配置主模型和备用模型 |
| 模型密钥 | 仓库内未跟踪的 `config/agent-models.local.yml`，只提交占位符模板 |
| 网络 | 可读取文档和 GitHub；允许受控依赖下载；外部写操作必须经过批准 |
| 工具执行 | 不提供任意 Shell；只允许注册的工具和参数模板 |
| 前端 | React + TypeScript + Vite；Spring Boot 提供 REST 和 SSE，生产时托管构建产物 |
| 用户系统 | 首期无用户系统，本机唯一操作者 |

MySQL 5.7.44 可以运行当前 V1-V4 迁移，但 Flyway Community 会提示该版本已超出官方支持范围。平台不依赖 MySQL 8 专有 SQL；升级到 MySQL 8.0+ 可获得更稳妥的长期维护边界。

### 2.1 当前实现状态

本文描述完整目标架构。当前第一阶段已经交付动态规划、并行 Implementer、独立 worktree、集成、强制验证、审查修复循环、模型路由、Token 统计、受限工具、只读 HTTPS 文档、持久事件、SSE、React 调度台和 GitHub push 一次性审批。

可靠性阶段仍有两个明确边界：进程重启后，执行中的 run 会保留证据并转为 `NEEDS_ATTENTION`，不会从中间模型调用自动续跑；`WAITING_FOR_INPUT` 的回答接口和控制台预算追加尚未开放。数据库中的 outbox、artifact 索引和租约字段已经预留，但当前单机调度仍直接使用进程内执行器。

## 3. 总体架构

```text
React 调度台
  | REST / SSE
Spring Boot 控制平面
  |-- ProjectService             项目注册、新建项目、worktree 管理
  |-- RunService                 任务生命周期、审批、暂停/恢复/取消
  |-- Scheduler                  领取可执行子任务、租约、并发与预算
  |-- AgentRuntime               Agent 状态机、检查点、上下文装配
  |-- CollaborationBoard         计划、契约、交接、决策、阻塞项
  |-- ModelGateway               模型路由、结构化输出、Token 统计、失败切换
  |-- ToolGateway                文件、Git、构建、测试、文档检索等受限工具
  |-- EventPublisher             事件、outbox、SSE 推送
  |-- LocalArtifactStore         本机保存完整日志、diff、模型原文和测试报告
  `-- MySQL                      任务元数据、事件、审批、用量和产物索引
```

模型调用、工具调用和状态流转都必须经过平台网关。Agent 不能直接连接数据库、执行 Shell、访问任意文件，或将任务状态直接改为完成。

## 4. Agent 角色和协作

首期注册七种角色。角色是可控能力集合，不是固定数量的进程。

| 角色 | 核心职责 | 默认能力 |
| --- | --- | --- |
| `PLANNER` | 分析需求、理解项目、任务拆分、验收标准 | 工作区只读、文档检索 |
| `ARCHITECT` | 模块边界、接口契约、关键设计决策 | 工作区只读、结构化决策 |
| `IMPLEMENTER` | 修改代码、局部验证 | 受限读写、格式化、构建测试 |
| `TESTER` | 编写/运行测试、基线对比、测试报告 | 测试文件写入、受限命令 |
| `REVIEWER` | 最终 diff、需求覆盖和安全审查 | 只读 |
| `INTEGRATOR` | 合并子分支、处理确定性冲突、集成验证 | 受限 Git、构建测试 |
| `RESEARCHER` | 读取官方文档、GitHub、整理可引用结论 | 只读网络、工作区只读 |

前端、后端、数据库等不是单独 Runtime 类型，而是 `IMPLEMENTER` 的专长标签和路径范围。Planner 可为同一角色创建多个并行任务。

Agent 通过版本化结构化消息协作：`Plan`、`TaskAssignment`、`Handoff`、`Decision`、`Blocker`、`TestReport`、`ReviewFinding`、`ApprovalRequest`。每条消息包含 schema 版本、run/task ID、发送者、接收者、创建时间、幂等 ID 和产物引用。

## 5. Agent Runtime 和状态机

每个 Agent 由 `AgentRuntime` 按以下循环执行：

```text
加载检查点和共享上下文
  -> 生成结构化下一步动作
  -> 校验动作和权限
  -> 调用工具或模型
  -> 持久化观察结果和检查点
  -> 判断继续、交接、等待输入、等待审批、完成或失败
```

Runtime 负责循环控制、动作校验、状态持久化、超时、Token/成本预算、重试和恢复。Agent 只负责自己的领域判断。

任务状态至少包括：

```text
CREATED -> PLANNING -> IMPLEMENTING -> TESTING -> REVIEWING -> COMPLETED
                                      |              |
                                      |              -> COMPLETED_WITH_WARNINGS
                                      v
                                NEEDS_ATTENTION

任意非终态可进入：WAITING_FOR_INPUT、WAITING_FOR_APPROVAL、PAUSED、FAILED、CANCELLED
```

默认预算：单任务 60 分钟、每个子任务 3 次修复循环、全局最多 2 次重新规划。达到预算后保留 worktree 和证据，进入 `NEEDS_ATTENTION`；用户可追加预算后从检查点继续。

## 6. 项目和 Git 工作区

### 已有项目

用户先在控制台注册允许的本机 Git 项目。平台解析真实路径，禁止路径和符号链接逃逸注册根目录。每次任务建立一个集成分支和集成 worktree；每个可写子任务再建立独立分支和子 worktree。

### 新建项目

用户只能在配置允许的父目录下指定新目录。平台确认目录不存在或为空后，在本地初始化 Git 仓库并建立初始提交。Planner 选择受控模板，或在重大技术选型不明确时转入 `WAITING_FOR_INPUT`。

### 集成

`INTEGRATOR` 把子任务的临时提交合并到任务集成分支。确定性冲突可自动解决；语义冲突或工作区状态不一致必须暂停。任务结束后用户可选择保留、归档或清理 worktree。

## 7. 模型网关

`ModelGateway` 统一处理供应商调用。首期适配 `mimo` 和 `deepseek` 的 OpenAI-compatible API，不在业务代码中绑定厂商 SDK 或模型名称。

本地私密配置位于 `config/agent-models.local.yml`，其示例文件只包含占位符。真实配置必须被 `.gitignore` 忽略，不得保存到数据库、事件、日志、任务快照或模型上下文。

每个角色可配置主模型和备用模型。网关统一输出以下 usage 字段：

```text
provider, model, input_tokens, output_tokens, reasoning_tokens,
cached_input_tokens, total_tokens, estimated_cost, latency_ms, request_status
```

提供商未返回用量时可估算，但必须标记为估算值。模型失败、超时或无法产生符合 schema 的输出时，可在预算内按角色策略切换备用模型。

## 8. 受控工具与安全边界

### 文件和命令

工具只接受平台注册的操作，不接受模型提供的任意 Shell 文本。首期工具包括：受限文件读取/写入、代码搜索、Git 状态/diff/分支/worktree、格式化、Maven/npm 构建测试、官方文档和 GitHub 只读检索。

`CommandRunner` 将操作映射为固定可执行程序和校验过的参数模板，并强制：

- 工作目录为任务 worktree
- 最小环境变量，不继承数据库密码、模型 Key 或 GitHub Token
- 命令超时、输出大小、CPU/内存和并发限制
- 禁止管道、重定向、任意脚本、任意下载执行和工作区外路径

项目依赖可自动下载。JDK/Node 等工具可在平台 `.tools/` 内按固定来源和校验值引导，但平台不修改系统全局 `PATH`、注册表或包管理器。

### 网络和外部写操作

Agent 可使用 `GET/HEAD` 读取官方文档和 GitHub 内容，响应大小和超时受限。网页、仓库内容、Issue、代码注释和工具输出都属于不可信数据，不能改变系统权限。

`git push`、创建/更新 PR、GitHub 评论和其他外部写操作必须创建 `ApprovalRequest`。控制台必须显示目标、待执行动作、完整 diff、测试证据和风险。每次批准只授权明确的一次动作。

### 敏感信息

输入、工具输出、日志和报告经过敏感信息识别与脱敏。发现疑似密码、Token、Cookie、私钥或连接串时，不得发给模型或持久化到远程数据库。高置信度泄露会暂停任务，并仅报告文件位置。

## 9. 持久化和产物

MySQL 是任务元数据的事实来源。首期使用 Flyway 建立以下核心表：

| 表 | 责任 |
| --- | --- |
| `projects` | 已注册项目和允许根目录 |
| `runs` | 顶层需求、任务状态、预算、版本和时间戳 |
| `agent_tasks` | 动态子任务、角色、租约、重试和结果摘要 |
| `agent_messages` | 结构化协作消息 |
| `run_events` | 追加式任务事件流 |
| `model_usages` | 模型调用用量和延迟统计 |
| `tool_executions` | 工具调用元数据和输出产物引用 |
| `artifacts` | 本机日志、diff、报告的路径与校验值 |
| `approvals` | 外部动作的申请和批准记录 |
| `outbox_messages` | 可靠触发调度和 SSE 事件的 outbox |

完整 prompt、模型响应、工具输出和测试日志仅保存到本机任务目录；MySQL 只保存摘要、路径、大小和 SHA-256。默认保留策略为 30 天，并允许按任务手动清理。

Worker 通过租约领取任务。应用启动时会恢复过期租约；模型请求和工具进程不假定可继续存在，必须从持久检查点安全重试或进入 `NEEDS_ATTENTION`。

## 10. 控制平面与调度台

REST API 提供项目注册、创建任务、查看任务树和事件、回答澄清问题、批准外部动作、追加预算、暂停、恢复和取消。

SSE 推送状态转换、Agent 生命周期、交接、模型 usage、工具执行、测试进度和审批请求。前端不通过定时轮询推测运行状态。

React 调度台第一阶段包含：

- 任务列表、状态筛选和任务时间线
- Agent/子任务依赖树及每个 Agent 的当前动作
- Agent 调用、工具调用、交接与重试事件
- 按任务、Agent、模型和时间维度的 Token、用量和估算成本统计
- diff、产物、测试结果和审查发现
- 等待输入、GitHub 审批、预算追加、暂停/恢复/取消操作

控制台默认只显示脱敏摘要；完整本机审计记录需要用户显式展开查看。

## 11. 首期实现切片

### Slice 1：平台基础

1. 增加 MySQL、Flyway、JDBC、配置绑定和测试数据库依赖。
2. 建立 Flyway 初始迁移、项目/任务/事件/Token usage 数据模型。
3. 用数据库仓储替换内存 `RunStore`，扩展任务状态机。
4. 保留一个无密钥可运行的本地 stub 模型，真实提供商按本地配置启用。

验收：应用可启动；创建任务、查询状态、重启后查询任务均可用；迁移可重复执行。

### Slice 2：受控项目执行

1. 实现项目注册和新建空白项目。
2. 实现路径校验、Git worktree、任务分支和本地产物目录。
3. 实现受限读取、补丁、Git 和 allowlist 命令工具。

验收：Agent 只能在当前任务 worktree 操作；不允许任意路径和任意 Shell；能完成本地 Git 任务隔离。

### Slice 3：自治 Runtime

1. 实现动态 Agent task、租约调度、检查点和恢复。
2. 实现 Planner、Implementer、Tester、Reviewer、Integrator 的最小闭环。
3. 建立结构化协作消息、预算、失败修复和审批状态。

验收：一项简单需求可被规划、实施、测试、审查和汇总；失败可在预算内修复或留存证据。

### Slice 4：模型与可观测性

1. 实现 `mimo` / `deepseek` 配置驱动的 OpenAI-compatible 网关。
2. 实现模型路由、备用模型、Token 归一化、成本估算和脱敏日志。
3. 发布持久事件和 SSE。

验收：控制台/API 可实时看到模型调用、Token、耗时、失败切换和任务状态。

### Slice 5：React 调度台

1. 新建 React + TypeScript + Vite 模块。
2. 实现任务列表、时间线、Agent 树、Token 面板、diff/测试查看和审批面板。
3. 构建产物由 Spring Boot 托管。

验收：本机浏览器可以完成创建任务、观察实时执行、处理输入/审批并查看最终报告。

### Slice 6：可靠性和 GitHub 审批

1. 完善恢复、并发、取消、限流、保留策略和安全扫描。
2. 实现本地 Git 提交准备和 GitHub 写操作审批网关。
3. 增加集成测试、端到端测试和故障注入测试。

验收：重启恢复、预算耗尽、命令超时、模型失败、审批拒绝和 GitHub 写入批准均有可验证行为。

## 12. 成功标准

首期完成后，用户可在本机控制台选择已有仓库或新建空目录，提交一个 Java/Spring、TypeScript/React 或二者结合的需求。平台应展示动态 Agent 调度和实时 Token 数据，在受控 worktree 中完成代码修改、测试、审查和报告；外部 GitHub 写入必须等待用户批准。
