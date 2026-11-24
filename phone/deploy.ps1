param(
    [switch]$NoBuild,
    [switch]$IncludeEmulator,
    [string]$TvIp = '192.168.1.121:5555'
)

$ErrorActionPreference = 'Stop'

$adb        = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }
$repoRoot   = Split-Path -Parent $PSScriptRoot
$phoneDir   = Join-Path $repoRoot 'phone'
$tvDir      = Join-Path $repoRoot 'tv'
$phoneApk   = Join-Path $phoneDir 'app\build\outputs\apk\debug\app-debug.apk'
$tvApk      = Join-Path $tvDir    'app\build\outputs\apk\debug\app-debug.apk'

function Invoke-Build($dir) {
    Write-Host "`n== Build: $dir"
    Push-Location $dir
    try {
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $dir" }
    }
    finally { Pop-Location }
}

function Get-Devices {
    $list = @()
    foreach ($line in (& $adb devices)) {
        if ($line -match '^(\S+)\s+device\b') { $list += $Matches[1] }
    }
    return $list
}

function Install-Apk($serial, $apk, $label) {
    if (-not $serial) { Write-Host "== $label : cihaz yok, atlanıyor"; return }
    if (-not (Test-Path $apk)) { throw "$label APK bulunamadı: $apk" }
    Write-Host "`n== $label -> $serial"
    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw "$label kurulumu başarısız" }
}

if (-not $NoBuild) {
    Invoke-Build $phoneDir
    Invoke-Build $tvDir
}

Write-Host "`n== TV adb connect: $tvIp"
& $adb connect $tvIp | Write-Host

$devices = Get-Devices
Write-Host "== Cihazlar: $($devices -join ', ')"

# TV: IP:port   Telefon: Wi-Fi ADB (tls-connect) veya USB   Emülatör: emulator-*
$tv    = $devices | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+:\d+$' } | Select-Object -First 1
$phone = $devices | Where-Object { $_ -match 'tls-connect' } | Select-Object -First 1
if (-not $phone) {
    $phone = $devices | Where-Object { $_ -notmatch '^(emulator-|\d+\.\d+\.\d+\.\d+:\d+$)' } | Select-Object -First 1
}
$emu = $devices | Where-Object { $_ -match '^emulator-' } | Select-Object -First 1

Install-Apk $tv    $tvApk  'TV sunucusu (com.lowell.tvremote)'
Install-Apk $phone  $phoneApk 'Telefon (com.lowell.tvkumanda)'
if ($IncludeEmulator) { Install-Apk $emu $phoneApk 'Emülatör' }

Write-Host "`nBitti. TV=$tv | Telefon=$phone"
