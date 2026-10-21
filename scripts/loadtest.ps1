# 简易压测脚本（PowerShell 7），无需安装 JMeter/wrk
#
# 用法：
#   pwsh -File scripts/loadtest.ps1                                  # 默认打 /api/shop/1，50 并发 10 秒
#   pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8080/api/shop-type/list -Concurrency 100 -DurationSeconds 20
#   pwsh -File scripts/loadtest.ps1 -Url http://127.0.0.1:8081/actuator/health -ApiKey your-api-key
#
# 输出：总请求数、QPS、平均/中位/P95/P99 延迟、错误率、状态码分布

param(
    [string]$Url = 'http://127.0.0.1:8080/api/shop/1',
    [int]$Concurrency = 50,
    [int]$DurationSeconds = 10,
    [string]$ApiKey = ''
)

if ($PSVersionTable.PSVersion.Major -lt 7) {
    Write-Error '需要 PowerShell 7+（ForEach-Object -Parallel）'
    exit 1
}

Write-Host "目标: $Url"
Write-Host "并发: $Concurrency, 时长: ${DurationSeconds}s`n"

$deadline = (Get-Date).AddSeconds($DurationSeconds)
$startedAt = Get-Date

$workers = 1..$Concurrency | ForEach-Object -Parallel {
    $u = $using:Url
    $end = $using:deadline
    $key = $using:ApiKey

    $client = [System.Net.Http.HttpClient]::new()
    $client.Timeout = [TimeSpan]::FromSeconds(10)
    if ($key) {
        [void]$client.DefaultRequestHeaders.Add('X-API-Key', $key)
    }

    $latencies = [System.Collections.Generic.List[double]]::new()
    $errors = 0
    $statuses = @{}

    while ((Get-Date) -lt $end) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $response = $client.GetAsync($u).GetAwaiter().GetResult()
            $code = [int]$response.StatusCode
            if ($code -ne 200) { $errors++ }
        } catch {
            $code = -1
            $errors++
        }
        $sw.Stop()
        $latencies.Add($sw.Elapsed.TotalMilliseconds)
        if ($statuses.ContainsKey($code)) { $statuses[$code]++ } else { $statuses[$code] = 1 }
    }
    $client.Dispose()

    [pscustomobject]@{
        Latencies = $latencies
        Errors    = $errors
        Statuses  = $statuses
    }
} -ThrottleLimit $Concurrency

$elapsed = ((Get-Date) - $startedAt).TotalSeconds
$all = [System.Collections.Generic.List[double]]::new()
$errors = 0
$statuses = @{}
foreach ($w in $workers) {
    foreach ($l in $w.Latencies) { $all.Add($l) }
    $errors += $w.Errors
    foreach ($k in $w.Statuses.Keys) {
        if ($statuses.ContainsKey($k)) { $statuses[$k] += $w.Statuses[$k] } else { $statuses[$k] = $w.Statuses[$k] }
    }
}

if ($all.Count -eq 0) {
    Write-Error '没有采集到请求，请检查目标地址
'
    exit 1
}

$sorted = $all | Sort-Object
function Get-Percentile($values, [double]$p) {
    $index = [Math]::Ceiling(($p / 100) * $values.Count) - 1
    if ($index -lt 0) { $index = 0 }
    return $values[$index]
}

$total = $all.Count
$avg = ($all | Measure-Object -Average).Average
$p50 = Get-Percentile $sorted 50
$p95 = Get-Percentile $sorted 95
$p99 = Get-Percentile $sorted 99
$max = ($all | Measure-Object -Maximum).Maximum
$qps = [Math]::Round($total / $elapsed, 1)
$errorRate = [Math]::Round(100.0 * $errors / $total, 2)

Write-Host ''
Write-Host '================ 压测结果 ================'
Write-Host ("总请求数 : {0}" -f $total)
Write-Host ("耗时     : {0:N1}s" -f $elapsed)
Write-Host ("QPS      : {0}" -f $qps)
Write-Host ("平均延迟 : {0:N1} ms" -f $avg)
Write-Host ("P50      : {0:N1} ms" -f $p50)
Write-Host ("P95      : {0:N1} ms" -f $p95)
Write-Host ("P99      : {0:N1} ms" -f $p99)
Write-Host ("最大延迟 : {0:N1} ms" -f $max)
Write-Host ("错误率   : {0}%" -f $errorRate)
Write-Host '状态码分布:'
$statuses.GetEnumerator() | Sort-Object Name | ForEach-Object { Write-Host ("  {0} -> {1}" -f $_.Key, $_.Value) }
