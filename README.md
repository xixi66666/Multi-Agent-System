# Multi-Agent System

一个在本机运行的自治多 Agent 编码平台。用户注册 Git 项目并提交需求后，平台会规划任务、动态组建 Agent 团队、隔离修改代码、执行验证和审查，并在 React 调度台实时展示 Agent、事件、Token、成本、worktree 和外部操作审批。

详细设计见 [自治多 Agent 平台设计与实施说明](docs/autonomous-multi-agent-platform-design.md)。

## 已实现能力

- 七种动态角色：`PLANNER`、`ARCHITECT`、`IMPLEMENTER`、`TESTER`、`REVIEWER`、`INTEGRATOR`、`RESEARCHER`
- Planner 根据需求和仓库内容生成 1 到 4 个可并行实施任务
- Agent 私有上下文加结构化 `Plan`、`Handoff`、`TestReport`、`ReviewFinding` 消息
- 已有 Git 项目注册，以及受控目录中的空白项目创建
- 每个 run 的集成 worktree 和每个并行写任务的独立 worktree/分支
- 受限文件读写、文本检索、固定 Maven/npm/Git 命令和只读 HTTPS 文档访问
- Tester 必须执行成功的已注册验证命令后才能完成
- Reviewer 拒绝后自动进入最多三轮修复、复测和复审
- `mimo`、`deepseek` OpenAI-compatible 模型路由、失败切换和本地 Stub 模式
- 运行时长、总 Token、工具轮次和修复轮次预算
- REST API、SSE 事件流和 React + TypeScript 调度台
- H2 本地默认存储；MySQL + Flyway 持久化可选
- GitHub push 一次性审批，并绑定 remote、branch、commit 和 diff 摘要

## 快速启动

环境需要 Git、Node.js/npm 和 PowerShell。JDK 21 会下载到仓库内的 `.tools/jdk-21`，Maven Wrapper 和依赖缓存也只使用仓库本地目录。

```powershell
.\start.ps1
```

打开 `http://127.0.0.1:8080`。服务只监听本机回环地址。

默认配置使用 `.data/` 下的 H2 文件数据库和 Stub 模型，不需要 API Key。Stub 模式只验证调度、持久化和控制台链路，不会让真实模型修改项目，任务最终状态为 `COMPLETED_WITH_WARNINGS`。

## 模型配置

真实配置位于被 Git 忽略的 `config/agent-models.local.yml`。仓库中的 `config/agent-models.example.yml` 包含 `mimo`、`deepseek` 和各角色路由的完整结构。

首次配置时复制示例，填写本机密钥和模型名，然后设置 `vibe.models.enabled: true`：

```powershell
Copy-Item config/agent-models.example.yml config/agent-models.local.yml
```

Planner 和 Reviewer 默认通过 `structured-output: true` 请求 JSON Object 输出，并在任务完成前校验结构。如果某个 OpenAI-compatible 供应商不支持 `response_format`，可在该 provider 下设置 `structured-output: false`；本地结构校验与自动重试仍然生效。

不要把 API Key 放入源码、README、提交记录或远程仓库。

## MySQL 配置

默认 H2 便于首次启动。要使用 MySQL，复制本地配置示例并只在忽略文件中填写连接信息：

```powershell
Copy-Item config/application-local.example.yml config/application-local.yml
```

创建空数据库 `vibe_agent` 后启动应用，Flyway 会自动执行 `src/main/resources/db/migration` 下的迁移。

当前迁移已在 MySQL 5.7.44 上验证可执行；Flyway Community 会提示 5.7 已超出其官方支持范围。长期运行建议升级到 MySQL 8.0+。数据库密码不得提交到 Git。

## 工作区配置

平台只能注册 `vibe.workspace.allowed-roots` 下的项目。MySQL 示例配置默认演示 `D:/Code`，请按本机目录修改。Agent 文件工具会再次校验真实路径，禁止绝对路径、路径逃逸和敏感配置文件访问。

空白项目必须位于已存在的允许父目录内，目标目录必须不存在或为空。平台会初始化 Git 仓库和第一个空提交。

## 调度流程

```text
PLANNER
  -> RESEARCHER / ARCHITECT（按需）
  -> IMPLEMENTER x N（独立 worktree，并行）
  -> INTEGRATOR
  -> TESTER
  -> REVIEWER
       -> 通过：COMPLETED
       -> 拒绝：IMPLEMENTER 修复 -> TESTER -> REVIEWER（预算内循环）
```

控制台可以创建和控制 run，查看 Agent 调用图、事件时间线、Token/成本、worktree 和审批队列。GitHub 外部写操作只有在用户明确批准一次后才会执行。

## 主要 API

```text
POST /api/projects
GET  /api/projects
POST /api/runs
GET  /api/runs/{id}
GET  /api/runs/{id}/tasks
GET  /api/runs/{id}/events
GET  /api/runs/{id}/events/stream
GET  /api/runs/{id}/usage
GET  /api/runs/{id}/workspaces
GET  /api/runs/{id}/messages
POST /api/runs/{id}/pause
POST /api/runs/{id}/resume
POST /api/runs/{id}/cancel
POST /api/runs/{id}/approvals/github-push
```

## 验证

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-21').Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = (Join-Path (Get-Location) '.m2')
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
.\mvnw.cmd '-Dmaven.repo.local=.m2/repository' test
```

## 第一阶段边界

这是可运行的单机 MVP，不包含多用户、租户、消息队列或自动部署。进程重启时，未完成 run 会保留数据库记录和 worktree 并转为 `NEEDS_ATTENTION`，当前版本不会从中间模型调用自动续跑。`WAITING_FOR_INPUT` 的澄清回复和控制台追加预算尚未开放；这些属于下一可靠性阶段，不影响当前从明确需求到代码、测试、审查和审批的主闭环。
