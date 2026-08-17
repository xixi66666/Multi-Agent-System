# Repository Guide

## Architecture

- `CoordinatorAgent` owns complete requirements, progress, sequencing, and final aggregation.
- Frontend, backend, and test agents implement `SpecializedAgent` and do not mutate run state directly.
- Model providers implement `AgentModel`; orchestration must not depend on a provider SDK.
- Frontend and backend work may run in parallel. Test work starts only after both results exist.

## Safety

- Never expose unrestricted shell, filesystem, URL, or Git operations as model tools.
- Workspace tools must resolve and validate paths under an explicitly allowed project root.
- Commands must use an allowlist and bounded timeouts.
- Credentials belong in ignored local configuration or environment variables and must never be logged.

## Verification

Use the project-local JDK and Maven repository:

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-21').Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = (Join-Path (Get-Location) '.m2')
.\mvnw.cmd '-Dmaven.repo.local=.m2/repository' test
```
