$ErrorActionPreference='Stop'
$developmentLogDirectory=Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Path $developmentLogDirectory -Force | Out-Null
Push-Location $PSScriptRoot
try {
    & mvn.cmd spring-boot:run *> (Join-Path $developmentLogDirectory 'development-server.log')
    exit $LASTEXITCODE
} finally { Pop-Location }
