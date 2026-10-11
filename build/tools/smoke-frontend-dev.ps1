#!/usr/bin/env pwsh
# 前端开发服务器冒烟测试。
#
# 目的：确认 `npm run dev` 能真正启动并响应 HTTP 请求，
# 而不是只看命令是否退出。测试完成后自动关闭进程。
#
# 用法：pwsh -File build/tools/smoke-frontend-dev.ps1

$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$fe = Join-Path $root '03-源码\frontend'
$tmp = Join-Path $root 'build\esbuild-tmp'
$port = 5173
$url = "http://127.0.0.1:$port"

New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# ---------------------------------------------------------------------------
# 确保后端已启动：前端代理需要真实后端才能验证端到端连通
# ---------------------------------------------------------------------------
$backendStarted = $false
$backendProc = $null
$backendUrl = 'http://127.0.0.1:8080/api/health/ping'

function Test-Backend {
    try {
        $r = Invoke-WebRequest $backendUrl -TimeoutSec 4 -UseBasicParsing -SkipHttpErrorCheck
        return $r.StatusCode -eq 200
    } catch {
        return $false
    }
}

if (-not (Test-Backend)) {
    $jar = Join-Path $root '03-源码\backend\target\wms-1.0.0.jar'
    if (Test-Path $jar) {
        Write-Host '=== 后端未运行，本次自动启动 ===' -ForegroundColor Cyan
        $bp = New-Object System.Diagnostics.ProcessStartInfo
        $bp.FileName = 'D:\Dev\jdk8\bin\java.exe'
        $bp.Arguments = '-Dfile.encoding=UTF-8 -jar "' + $jar + '"'
        $bp.WorkingDirectory = (Join-Path $root '03-源码\backend')
        $bp.UseShellExecute = $false
        $bp.RedirectStandardOutput = $true
        $bp.RedirectStandardError = $true
        $bp.CreateNoWindow = $true
        $bp.EnvironmentVariables['JAVA_HOME'] = 'D:\Dev\jdk8'
        # 数据库口令从用户级环境变量继承；若未设置则使用默认测试账号
        if (-not $bp.EnvironmentVariables.ContainsKey('SPRING_DATASOURCE_USERNAME')) {
            $bp.EnvironmentVariables['SPRING_DATASOURCE_USERNAME'] = 'wms'
        }
        if (-not $bp.EnvironmentVariables.ContainsKey('SPRING_DATASOURCE_PASSWORD')) {
            $bp.EnvironmentVariables['SPRING_DATASOURCE_PASSWORD'] = 'Wms@2026App'
        }
        $backendProc = New-Object System.Diagnostics.Process
        $backendProc.StartInfo = $bp
        [void]$backendProc.Start()
        $null = $backendProc.StandardOutput.ReadToEndAsync()

        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 2
            if (Test-Backend) { break }
        }
        $backendStarted = Test-Backend
        if ($backendStarted) { Write-Host '  后端已就绪' -ForegroundColor Green }
        else { Write-Host '  后端启动超时' -ForegroundColor Yellow }
    } else {
        Write-Host '=== 未找到后端 JAR，跳过端到端代理验证 ===' -ForegroundColor Yellow
        Write-Host ('  期望路径：' + $jar)
    }
} else {
    Write-Host '=== 后端已在运行，复用现有实例 ===' -ForegroundColor Cyan
}

Write-Host '=== 启动前端开发服务器 ===' -ForegroundColor Cyan

# 用 ProcessStartInfo 直接启动，便于控制环境变量与输出重定向
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'cmd.exe'
$psi.Arguments = '/c npm run dev'
$psi.WorkingDirectory = $fe
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
# 临时目录指向工作区内，避免 esbuild 中间文件落在受限位置
$psi.EnvironmentVariables['TMP'] = $tmp
$psi.EnvironmentVariables['TEMP'] = $tmp
$psi.EnvironmentVariables['TMPDIR'] = $tmp

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()
$outTask = $proc.StandardOutput.ReadToEndAsync()
$errTask = $proc.StandardError.ReadToEndAsync()

$pass = 0; $fail = 0
function Check($name, $condition, $detail) {
    if ($condition) { Write-Host ("  [PASS] {0,-38} {1}" -f $name, $detail) -ForegroundColor Green; $script:pass++ }
    else            { Write-Host ("  [FAIL] {0,-38} {1}" -f $name, $detail) -ForegroundColor Red;   $script:fail++ }
}

# 轮询等待服务就绪（最多 60 秒）
$ready = $false
$waited = 0
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $waited += 2
    if ($proc.HasExited) { break }
    try {
        $r = Invoke-WebRequest $url -TimeoutSec 4 -UseBasicParsing -SkipHttpErrorCheck
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        # 尚未就绪，继续等待
    }
}

Check 'dev server responds on :5173' $ready ("waited=" + $waited + "s")

if ($ready) {
    # 校验返回的 HTML 是项目首页骨架
    $r = Invoke-WebRequest $url -TimeoutSec 6 -UseBasicParsing -SkipHttpErrorCheck
    $html = $r.Content
    Check 'HTML contains app mount point' ($html -match 'id="app"') ("length=" + $html.Length)
    Check 'HTML contains loading skeleton' ($html -match 'app-loading') 'first-paint placeholder present'
    Check 'HTML title is set' ($html -match '智慧仓储管理系统') 'title present'
    Check 'entry script injected' ($html -match '/src/main.ts') 'module entry found'

    # 校验 Vite 能编译并返回主入口（这一步会真正走一遍模块转换）
    try {
        $main = Invoke-WebRequest "$url/src/main.ts" -TimeoutSec 15 -UseBasicParsing -SkipHttpErrorCheck
        Check 'main.ts transforms OK' ($main.StatusCode -eq 200) ("http=" + $main.StatusCode + " bytes=" + $main.Content.Length)
    } catch {
        Check 'main.ts transforms OK' $false $_.Exception.Message
    }

    # 校验代理配置生效：经前端 5173 访问 /api 应被转发到后端 8080 并返回成功
    try {
        $api = Invoke-WebRequest "$url/api/health/ping" -TimeoutSec 8 -UseBasicParsing -SkipHttpErrorCheck
        Check 'proxy /api reaches backend' ($api.StatusCode -eq 200) ("http=" + $api.StatusCode + " body=" + $api.Content)
    } catch {
        Check 'proxy /api reaches backend' $false ("转发失败：" + $_.Exception.Message.Split([char]10)[0])
    }
}

Write-Host ''
Write-Host '=== 关闭开发服务器 ===' -ForegroundColor Cyan
if (-not $proc.HasExited) {
    # 结束整个进程树：npm 会派生子进程
    & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null
    try { $proc.WaitForExit(8000) | Out-Null } catch { }
    Write-Host '  已停止'
}

# 仅关闭本次由脚本启动的后端，复用已有实例时不干预
if ($backendStarted -and $backendProc -and -not $backendProc.HasExited) {
    Write-Host '=== 关闭本次启动的后端 ===' -ForegroundColor Cyan
    & taskkill /PID $backendProc.Id /T /F 2>&1 | Out-Null
    try { $backendProc.WaitForExit(8000) | Out-Null } catch { }
    Write-Host '  已停止'
}

$log = ''
try { $log = $outTask.Result } catch { }
if ($log -and $log -match 'error|Error|ERROR') {
    Write-Host ''
    Write-Host '启动日志中的错误行：' -ForegroundColor Yellow
    ($log -split "`r?`n") | Where-Object { $_ -match 'error|Error|ERROR' } | Select-Object -First 8 |
        ForEach-Object { Write-Host ('  ' + $_.Trim()) }
}

Write-Host ''
Write-Host ("=== RESULT: {0} passed, {1} failed ===" -f $pass, $fail)
if ($fail -eq 0) { Write-Host 'FRONTEND DEV SMOKE TEST PASSED' -ForegroundColor Green; exit 0 }
else { Write-Host 'FRONTEND DEV SMOKE TEST FAILED' -ForegroundColor Red; exit 3 }
