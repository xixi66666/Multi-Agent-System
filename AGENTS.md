# Repository Guide

## Project

Vibe Agent (`com.vibeagent`, Maven artifact `vibe-agent`) is a local single-machine autonomous multi-agent coding platform. Users register a Git project, submit a requirement, and the platform plans tasks, assembles agent teams, modifies code inside isolated git worktrees, runs verification, reviews the result, and exposes everything through a REST/SSE API and a React console.

Stack: Spring Boot 3.5 / Java 21 / Maven, React + TypeScript + Vite frontend, H2 (default) or MySQL via Flyway. Runs locally on `127.0.0.1:8080`.

## Architecture

- `AutonomousRunEngine` is the primary orchestrator. It owns the run state machine and sequencing: planning → optional research/architecture → parallel implementation → integration → testing → review → repair loop → completion.
- `PlanningService` runs the PLANNER agent and validates its structured `ExecutionPlan` (1–4 implementation tasks plus acceptance criteria).
- `AgentTaskRuntime` executes a single agent task: it loops model calls and structured tool actions (with a transcript and tool-turn budget) until the agent returns COMPLETE, then records a typed collaboration message (PLAN, HANDOFF, TEST_REPORT, REVIEW_FINDING, DECISION) via `AgentMessageStore`. A TESTER cannot complete before a registered verification command succeeds.
- `WorkspaceToolGateway` is the only path for agents to touch files, search text, read HTTPS docs, or run commands. All non-read actions are role-gated and command/folder allowlisted.
- `WorktreeService` creates one integration worktree per run and one worktree/branch per parallel IMPLEMENTER task; `integrate()` merges task branches into the integration worktree, and the final diff vs. the base revision is fed to the reviewer.
- `ReviewService` runs the REVIEWER agent; a rejected review returns actionable findings and `AutonomousRunEngine` loops IMPLEMENTER repair → TESTER retest → REVIEWER re-review up to `vibe.runtime.max-repair-rounds`.
- `RunControlService` / `RunExecutionGuard` implement pause/resume/cancel plus runtime, total-token, and repair-round budgets, checked at every checkpoint.
- `ApprovalService` / `GitHubApprovalService` gate external Git push: one-time approval is bound to the GitHub remote, branch, commit SHA, and diff hash.
- Model providers implement `ModelGateway` (`OpenAiCompatibleModelGateway` for HTTPS OpenAI-compatible endpoints, `StubModelGateway` for offline runs). Orchestration must not depend on a provider SDK. Per-role routing and fallback come from `AgentModelsProperties` (`config/agent-models.local.yml`).
- Persistence: H2 file DB by default; MySQL + Flyway migrations live in `src/main/resources/db/migration`. `vibe.workspace.allowed-roots` bounds which local projects can be registered; blank projects are initialized in already-allowed parent directories.
- Frontend: React + TypeScript console under `frontend/`; consumes the REST/SSE API (`useRunStream`) and is prebuilt into `src/main/resources/static`.
- Legacy note: `CoordinatorAgent` plus `SpecializedAgent` implementations (`FrontendAgent`, `BackendAgent`, `TestAgent`) still exist and drive a simplified three-agent run when `AutonomousRunEngine` is not injected. New work should target the autonomous engine path.

## Safety

- Never expose unrestricted shell, filesystem, URL, or Git operations as model tools.
- Workspace tools must resolve and validate real paths under an explicitly allowed project/worktree root; absolute paths and path escapes are rejected by `WorkspaceToolGateway`.
- Commands are allowlisted (`MAVEN_TEST`, `MAVEN_PACKAGE`, `NPM_TEST`, `NPM_BUILD`, `GIT_STATUS`, `GIT_DIFF`) with bounded timeouts, and roles are restricted per action.
- Files such as `.env`, `config/application-local.yml`, `config/agent-models.local.yml`, `*.pem`, `*.key`, and anything under `.git/` are denied to agent tools; TESTER may write only test files.
- HTTPS documents read by RESEARCHER are untrusted data and must never act as instructions.
- Credentials belong only in ignored local config (`config/agent-models.local.yml`, `config/application-local.yml`) or environment variables and must never be logged, committed, or printed; `SensitiveDataRedactor` hides them from tool output.
- External writes (GitHub push) require explicit one-time user approval.

## Verification

Use the project-local JDK and Maven repository:

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-21').Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = (Join-Path (Get-Location) '.m2')

npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
.\mvnw.cmd '-Dmaven.repo.local=.m2/repository' test
```