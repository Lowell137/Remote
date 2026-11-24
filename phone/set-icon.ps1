param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dark', 'solar', 'mono', 'custom')]
    [string]$Icon
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$phoneDir = Join-Path $repoRoot 'phone'
$tvDir    = Join-Path $repoRoot 'tv'

$phoneSrc = Join-Path $phoneDir "app\src\main\res\drawable\ic_launcher_$Icon.xml"
$phoneDst = Join-Path $phoneDir 'app\src\main\res\drawable\ic_launcher.xml'
$tvSrc    = Join-Path $tvDir "app\src\main\res\drawable\ic_launcher_fg_$Icon.xml"
$tvDst    = Join-Path $tvDir 'app\src\main\res\drawable\ic_launcher_fg.xml'

foreach ($f in @($phoneSrc, $tvSrc)) {
    if (-not (Test-Path $f)) { throw "İkon varyantı yok: $f" }
}

Copy-Item $phoneSrc $phoneDst -Force
Copy-Item $tvSrc $tvDst -Force
"# Aktif ikon: $Icon" | Set-Content -Path (Join-Path $phoneDir 'icon.txt') -Encoding UTF8

Write-Host "== İkon: $Icon -> TV + telefon"
& (Join-Path $phoneDir 'deploy.ps1')
