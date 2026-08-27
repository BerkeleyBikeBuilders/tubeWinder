# Build release\TubeWinder-win64.zip: the standalone bundle handed to the club.
#
#     powershell -ExecutionPolicy Bypass -File .\tools\make-bundle.ps1
#
# The zip contains the app, its jar, and a complete Java runtime, so it needs nothing installed on
# the machine that runs it. Extract the folder, double-click TubeWinder.bat. This is how UGS ships:
# its download carries its own jdk\ folder too.
#
# Only run this when the release needs refreshing. Day to day, TubeWinder.bat at the top of the
# repo is the thing to use, because it rebuilds when you pull and this does not.

param(
    # Temurin runtime to bundle. Any Windows x64 JRE zip will do.
    [string]$RuntimeUrl = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jre_x64_windows_hotspot_21.0.5_11.zip'
)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'TubeWinder.jar'
$release = Join-Path $repo 'release'
$work = Join-Path (Join-Path $repo 'target') 'bundle'
$staged = Join-Path $work 'TubeWinder'
$zip = Join-Path $release 'TubeWinder-win64.zip'
$cache = Join-Path (Join-Path $repo 'target') 'runtime-download.zip'

# 1. Make sure the jar is current.
& (Join-Path $PSScriptRoot 'launch.ps1') -NoLaunch
if (-not (Test-Path $jar)) {
    Write-Error 'TubeWinder.jar was not built.'
}

# 2. Fetch the Windows runtime, once. It is about 50 MB.
if (-not (Test-Path $cache)) {
    Write-Host 'Downloading the Java runtime (about 50 MB, once)...'
    New-Item -ItemType Directory -Path (Split-Path -Parent $cache) -Force | Out-Null
    Invoke-WebRequest -Uri $RuntimeUrl -OutFile $cache
}

# 3. Lay the bundle out.
if (Test-Path $work) {
    Remove-Item $work -Recurse -Force
}
New-Item -ItemType Directory -Path $staged -Force | Out-Null

Write-Host 'Unpacking the runtime...'
$unpacked = Join-Path $work 'runtime-unpacked'
Expand-Archive -Path $cache -DestinationPath $unpacked -Force
$runtimeRoot = Get-ChildItem $unpacked -Directory | Select-Object -First 1
if (-not $runtimeRoot) {
    Write-Error "The runtime archive did not contain a folder."
}
Move-Item $runtimeRoot.FullName (Join-Path $staged 'runtime')
Remove-Item $unpacked -Recurse -Force

Copy-Item $jar $staged
Copy-Item (Join-Path $PSScriptRoot 'bundle-launcher.bat') (Join-Path $staged 'TubeWinder.bat')
Copy-Item (Join-Path $PSScriptRoot 'bundle-readme.txt') (Join-Path $staged 'README.txt')

# 4. Zip it.
New-Item -ItemType Directory -Path $release -Force | Out-Null
if (Test-Path $zip) {
    Remove-Item $zip -Force
}
Write-Host 'Compressing...'
Compress-Archive -Path $staged -DestinationPath $zip -CompressionLevel Optimal

$size = [math]::Round((Get-Item $zip).Length / 1MB)
Write-Host ''
Write-Host "Built $zip ($size MB)"
Write-Host 'Extract that anywhere and double-click TubeWinder.bat. Nothing needs installing.'
