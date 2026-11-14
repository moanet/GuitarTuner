<#
.SYNOPSIS
    打包 Android 项目源码为干净的 zip（排除 build/.gradle/local.properties 等）。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\pack-source.ps1
    .\pack-source.ps1 -OutputPath D:\backup
#>
[CmdletBinding()]
param(
    [string]$OutputPath,
    [switch]$KeepStaging
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# 1. 定位项目根目录
$ProjectRoot = $PSScriptRoot
if (-not $ProjectRoot) { $ProjectRoot = (Get-Location).Path }
Write-Host "项目根目录: $ProjectRoot" -ForegroundColor Cyan

# 2. 校验确实是 Android 项目根
$checks = @(
    @{ Name = 'settings.gradle.kts'; Alt = 'settings.gradle' },
    @{ Name = 'build.gradle.kts';    Alt = 'build.gradle' },
    @{ Name = 'gradlew.bat';         Alt = $null },
    @{ Name = 'app';                 Alt = $null }
)
$missing = @()
foreach ($c in $checks) {
    $ok = Test-Path (Join-Path $ProjectRoot $c.Name)
    if (-not $ok -and $c.Alt) { $ok = Test-Path (Join-Path $ProjectRoot $c.Alt) }
    if (-not $ok) { $missing += ($c.Name + $(if ($c.Alt) { " / $($c.Alt)" } else { "" })) }
}
if ($missing.Count -gt 0) {
    Write-Host "错误：当前目录不像 Android 项目根，缺少: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}

# 3. 准备输出路径与暂存目录
$ProjectName = Split-Path $ProjectRoot -Leaf
$Timestamp   = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $OutputPath) { $OutputPath = Split-Path $ProjectRoot -Parent }
if (-not (Test-Path $OutputPath)) { New-Item -ItemType Directory -Path $OutputPath -Force | Out-Null }
$ZipPath = Join-Path $OutputPath "$ProjectName-src-$Timestamp.zip"

$Staging = Join-Path $env:TEMP "$ProjectName-src-stage-$Timestamp"
if (Test-Path $Staging) { Remove-Item $Staging -Recurse -Force }
New-Item -ItemType Directory -Path $Staging -Force | Out-Null

# 4. robocopy 复制并排除
$excludeDirs = @(
    'build', '.gradle', '.kotlin', '.idea', '.git',
    'captures', '.cxx', '.externalNativeBuild', 'out', 'bin', 'gen'
)
$excludeFiles = @(
    'local.properties',
    'pack-source.ps1', 'pack-source.bat',
    '*.apk', '*.aab', '*.ap_', '*.dex', '*.class', '*.iml', '*.log', '*.zip',
    'Thumbs.db', '.DS_Store'
)
$roboArgs = @($ProjectRoot, $Staging, '/E', '/XD') + $excludeDirs +
            @('/XF') + $excludeFiles +
            @('/NFL', '/NDL', '/NJH', '/NJS', '/NP')

Write-Host "复制源码中..." -ForegroundColor Cyan
& robocopy @roboArgs | Out-Null
if ($LASTEXITCODE -ge 8) {
    Write-Host "robocopy 失败，退出码 $LASTEXITCODE" -ForegroundColor Red
    if (-not $KeepStaging) { Remove-Item $Staging -Recurse -Force -ErrorAction SilentlyContinue }
    exit 1
}

# 5. 压缩
Write-Host "压缩中..." -ForegroundColor Cyan
if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path (Join-Path $Staging '*') -DestinationPath $ZipPath -CompressionLevel Optimal

# 6. 校验 zip 内容
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
try {
    $entries = $zip.Entries
    $count   = $entries.Count
    $sizeKB  = [math]::Round((Get-Item $ZipPath).Length / 1KB, 1)
    $norm = { param($n) $n -replace '\\', '/' }
    $hasWrapperJar = @($entries | Where-Object { (& $norm $_.FullName) -match 'gradle/wrapper/gradle-wrapper\.jar$' }).Count -gt 0
    $hasManifest   = @($entries | Where-Object { (& $norm $_.FullName) -match 'app/src/main/AndroidManifest\.xml$' }).Count -gt 0
    $hasLocalProps = @($entries | Where-Object { (& $norm $_.FullName) -match '(^|/)local\.properties$' }).Count -gt 0
    $hasBuildDir   = @($entries | Where-Object { (& $norm $_.FullName) -match '(^|/)build/' }).Count -gt 0
    $hasApk        = @($entries | Where-Object { (& $norm $_.FullName) -match '\.(apk|aab)$' }).Count -gt 0
} finally {
    $zip.Dispose()
}

# 7. 输出结果（状态标记全部用 ASCII，避免编码问题）
Write-Host ""
Write-Host "=== 打包完成 ===" -ForegroundColor Green
Write-Host "输出文件 : $ZipPath"
Write-Host "大小     : $sizeKB KB"
Write-Host "文件数   : $count"
$mark = { param($b) if ($b) { '[OK]' } else { '[X]' } }
$okColor = { param($b) if ($b) { 'Green' } else { 'Red' } }

Write-Host ("gradle-wrapper.jar : {0} {1}" -f (& $mark $hasWrapperJar), $(if ($hasWrapperJar) { '有' } else { '缺失' })) -ForegroundColor (& $okColor $hasWrapperJar)
Write-Host ("AndroidManifest.xml: {0} {1}" -f (& $mark $hasManifest),   $(if ($hasManifest)   { '有' } else { '缺失' })) -ForegroundColor (& $okColor $hasManifest)
Write-Host ("local.properties   : {0} {1}" -f (& $mark (-not $hasLocalProps)), $(if ($hasLocalProps) { '混入（应排除）' } else { '已排除' })) -ForegroundColor (& $okColor (-not $hasLocalProps))
Write-Host ("build/ 目录        : {0} {1}" -f (& $mark (-not $hasBuildDir)),   $(if ($hasBuildDir)   { '混入（应排除）' } else { '已排除' })) -ForegroundColor (& $okColor (-not $hasBuildDir))
Write-Host ("APK/AAB            : {0} {1}" -f (& $mark (-not $hasApk)),        $(if ($hasApk)        { '混入（应排除）' } else { '已排除' })) -ForegroundColor (& $okColor (-not $hasApk))

if (-not $hasWrapperJar -or $hasBuildDir -or $hasLocalProps) {
    Write-Host ""
    Write-Host "注意：上述校验未全绿，请检查后再分发。" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "校验通过，可直接分发。" -ForegroundColor Green
}

# 8. 清理
if (-not $KeepStaging) {
    Remove-Item $Staging -Recurse -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "已保留暂存目录: $Staging" -ForegroundColor DarkGray
}