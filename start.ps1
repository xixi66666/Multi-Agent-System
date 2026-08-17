$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:JAVA_HOME = & (Join-Path $projectRoot 'scripts\bootstrap-jdk.ps1') | Select-Object -Last 1
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = Join-Path $projectRoot '.m2'
$frontendRoot = Join-Path $projectRoot 'frontend'
if (Test-Path -LiteralPath (Join-Path $frontendRoot 'package.json')) {
    $env:npm_config_cache = Join-Path $projectRoot '.npm-cache'
    if (-not (Test-Path -LiteralPath (Join-Path $frontendRoot 'node_modules'))) {
        & npm.cmd --prefix $frontendRoot ci
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    }
    & npm.cmd --prefix $frontendRoot run build
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
& (Join-Path $projectRoot 'mvnw.cmd') '-Dmaven.repo.local=.m2/repository' 'spring-boot:run'
exit $LASTEXITCODE
