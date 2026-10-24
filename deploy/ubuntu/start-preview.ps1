# Run on the user's Windows PC, not on Ubuntu. No secrets are embedded here.
$ErrorActionPreference = 'Stop'
$previewUrl = 'http://127.0.0.1:18080/'
$keyFile = 'D:\workplace\deployment-private\boom_ed25519.windows'
$hostsFile = 'D:/workplace/tmp/boom-known-hosts'
if (-not (Test-Path -LiteralPath $keyFile)) { throw 'Deployment key not found on this PC.' }

function Test-BoomPreview {
    try {
        $response = Invoke-WebRequest -Uri $previewUrl -TimeoutSec 2 -UseBasicParsing
        return $response.StatusCode -eq 200 -and $response.Content.Contains('BOOM')
    } catch { return $false }
}

if (Test-BoomPreview) {
    Write-Host "Cloud preview is already available: $previewUrl"
    return
}
$listener = Get-NetTCPConnection -LocalPort 18080 -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw 'Port 18080 is occupied by another listener; inspect it before starting a tunnel.' }
$sshProgram = Join-Path $env:WINDIR 'System32\OpenSSH\ssh.exe'
$arguments = @('-N', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
               '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30',
               '-o', 'ServerAliveCountMax=3', '-o', 'StrictHostKeyChecking=yes',
               '-o', "UserKnownHostsFile=$hostsFile", '-i', $keyFile,
               '-L', '127.0.0.1:18080:127.0.0.1:80', 'root@121.40.150.252')
$tunnelProcess = Start-Process -FilePath $sshProgram -ArgumentList $arguments -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 15; $attempt++) {
    if (Test-BoomPreview) {
        Write-Host "Cloud preview is available: $previewUrl"
        Write-Host "Local SSH tunnel PID: $($tunnelProcess.Id)"
        return
    }
    if ($tunnelProcess.HasExited) { throw 'SSH tunnel exited; verify connectivity and key permissions.' }
    Start-Sleep -Milliseconds 500
}
throw 'Preview did not respond; inspect SSH connectivity. No server services were changed.'
