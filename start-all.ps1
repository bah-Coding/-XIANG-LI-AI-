# Start Redis (VM or local Memurai), backend and nginx if their ports are not listening.
# Local deployment for E:\hmdp-Java (AYO77-code/hmdp-Java)
$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
# Optional: load REDIS_HOST / REDIS_PASSWORD / DB_* from local.env.ps1 (ASCII only!)
$envFile = Join-Path $here 'local.env.ps1'
if (Test-Path $envFile) { . $envFile }
if (-not $env:REDIS_HOST) { $env:REDIS_HOST = 'localhost' }

function Test-Port([int]$Port, [string]$Computer = '127.0.0.1') {
    return (Test-NetConnection -ComputerName $Computer -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue)
}

$RedisExe   = 'E:\Programs\Memurai\Memurai\memurai.exe'
$RedisConf  = 'E:\Programs\Memurai\memurai.conf'
$JavaExe    = 'D:\Programs\Java\jdk17\bin\java.exe'
$Jar        = 'E:\hmdp-Java\AI-dianping-backend\target\AI-dianping-backend-0.0.1-SNAPSHOT.jar'
$BackendDir = 'E:\hmdp-Java\AI-dianping-backend'
$FrontDir   = 'E:\hmdp-Java\AI-dianping-frontend'
$LogDir     = 'E:\hmdp-Java\logs'
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

$isLocalRedis = ($env:REDIS_HOST -in @('localhost', '127.0.0.1'))
if ($isLocalRedis) {
    if (-not (Test-Port 6379)) {
        Write-Host '[1/3] starting local Redis (Memurai) on 6379 ...'
        Start-Process -FilePath $RedisExe -ArgumentList $RedisConf -WorkingDirectory (Split-Path $RedisExe) -WindowStyle Hidden
        Start-Sleep -Seconds 3
    } else {
        Write-Host '[1/3] local Redis already listening on 6379'
    }
} else {
    if (Test-Port 6379 $env:REDIS_HOST) {
        Write-Host "[1/3] using Redis in VM at $($env:REDIS_HOST):6379"
    } else {
        Write-Warning "[1/3] cannot reach Redis at $($env:REDIS_HOST):6379 - is the VM running?"
        Write-Warning "      start VM: D:\vmrun.exe -T ws start `"E:\vmimages\centos9\CentOS8.vmx`" nogui"
    }
}

if (-not (Test-Port 8081)) {
    Write-Host '[2/3] starting backend on 8081 ...'
    # logback 只认系统属性，不认环境变量，所以把 LOG_PATH 转成 -DLOG_PATH
    $javaArgs = @('-jar', $Jar)
    if ($env:LOG_PATH) { $javaArgs = @("-DLOG_PATH=$($env:LOG_PATH)") + $javaArgs }
    Start-Process -FilePath $JavaExe -ArgumentList $javaArgs -WorkingDirectory $BackendDir -WindowStyle Hidden `
        -RedirectStandardOutput "$LogDir\backend.out.log" -RedirectStandardError "$LogDir\backend.err.log"
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        if (Test-Port 8081) { break }
    }
} else {
    Write-Host '[2/3] backend already listening on 8081'
}

if (-not (Test-Port 8080)) {
    Write-Host '[3/3] starting nginx on 8080 ...'
    Start-Process -FilePath "$FrontDir\nginx.exe" -ArgumentList @('-p', "$FrontDir/", '-c', 'conf/nginx.conf') `
        -WorkingDirectory $FrontDir -WindowStyle Hidden
    Start-Sleep -Seconds 2
} else {
    Write-Host '[3/3] nginx already listening on 8080'
}

Write-Host ''
Write-Host ("Redis   6379 : " + $env:REDIS_HOST + " -> " + (Test-Port 6379 $env:REDIS_HOST))
Write-Host ('Backend 8081 : ' + (Test-Port 8081))
Write-Host ('Front   8080 : ' + (Test-Port 8080))
Write-Host ''
Write-Host 'Open http://localhost:8080  (AI chat needs env API_KEY)'
