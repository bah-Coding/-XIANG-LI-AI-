# Stop nginx (8080), backend (8081) and Redis/Memurai (6379) by listening port.
$ErrorActionPreference = 'Continue'

# Prefer nginx's own graceful stop so worker processes do not keep port 8080 open.
$frontDir = 'E:\hmdp-Java\AI-dianping-frontend'
if (Test-Path "$frontDir\nginx.exe") {
    Push-Location $frontDir
    & "$frontDir\nginx.exe" -p "$frontDir/" -c conf/nginx.conf -s stop 2>$null
    Pop-Location
    Start-Sleep -Seconds 2
}

function Stop-ByPort([int]$Port, [string]$Name) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) {
        Write-Host "$Name ($Port) not running"
        return
    }
    $owners = $conns | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($procId in $owners) {
        try {
            Stop-Process -Id $procId -Force -ErrorAction Stop
            Write-Host "stopped $Name (port $Port, pid $procId)"
        } catch {
            Write-Host "failed to stop $Name (port $Port, pid $procId): $_"
        }
    }
}

Stop-ByPort 8080 'nginx'
Stop-ByPort 8081 'backend'
Stop-ByPort 6379 'redis'
