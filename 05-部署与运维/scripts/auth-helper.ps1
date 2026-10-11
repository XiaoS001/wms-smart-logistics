# 验证脚本共享的认证辅助。
#
# 背景：后端已启用统一的认证与权限拦截器（AuthInterceptor），
# 除 /api/auth/login 与健康检查外，所有 /api/** 接口都要求
# Authorization: Bearer <token>，并按接口所需权限校验。
# 因此各验证脚本必须先登录取得令牌。
#
# 用法（在脚本的 Api 函数定义之后）：
#     . "$PSScriptRoot\_auth-helper.ps1"
#     Initialize-Auth
# 之后在 Api 函数内加入：
#     $h = Get-AuthHeaders
#     if ($h) { $p.Headers = $h }
#
# 令牌保存在 $global:WMS_TOKEN，跨函数可见。

$global:WMS_TOKEN = $null
$global:WMS_AUTH_USER = 'admin'
$global:WMS_AUTH_PASS = '123456'

<#
.SYNOPSIS
    登录并保存令牌到 $global:WMS_TOKEN。
.DESCRIPTION
    默认使用管理员账号：验证脚本需要覆盖全部模块，只有管理员拥有全部权限。
.PARAMETER Username
    登录名，默认 admin。
.PARAMETER Password
    口令，默认 123456。
.PARAMETER BaseUrl
    服务地址，默认 http://127.0.0.1:8080。
#>
function Initialize-Auth {
    param(
        [string]$Username = 'admin',
        [string]$Password = '123456',
        [string]$BaseUrl = 'http://127.0.0.1:8080'
    )
    $body = '{"username":"' + $Username + '","password":"' + $Password + '"}'
    try {
        $r = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method Post -Body $body `
             -ContentType 'application/json' -UseBasicParsing -TimeoutSec 30 -SkipHttpErrorCheck
        $j = $r.Content | ConvertFrom-Json
        if ($j.code -eq 200 -and $j.data.token) {
            $global:WMS_TOKEN = $j.data.token
            $global:WMS_AUTH_USER = $Username
            return $true
        }
        Write-Host ("  登录失败: " + $j.message) -ForegroundColor Red
        return $false
    } catch {
        Write-Host ("  登录异常: " + $_.Exception.Message) -ForegroundColor Red
        return $false
    }
}

<#
.SYNOPSIS
    返回带 Authorization 头的哈希表；未登录时返回 $null。
#>
function Get-AuthHeaders {
    if (-not $global:WMS_TOKEN) { return $null }
    return @{ Authorization = "Bearer $($global:WMS_TOKEN)" }
}
