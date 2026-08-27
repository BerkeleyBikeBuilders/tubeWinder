# Build a standalone Tube Winder application, the way UGS ships.
#
#     powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1
#
# Produces dist\TubeWinder\TubeWinder.exe with a Java runtime bundled inside it. Anyone can run
# that without installing Java at all: copy the whole dist\TubeWinder folder to their machine and
# double-click the exe.
#
# This is the answer for handing the app to the rest of the team. For your own machine, where the
# source is already checked out, TubeWinder.bat is better: it rebuilds when you pull, and this does
# not.
#
# Needs a full JDK 17 or newer. The JetBrains runtime bundled with IntelliJ often has no jpackage;
# if that is all you have, install Temurin:
#     winget install --id EclipseAdoptium.Temurin.21.JDK -e

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'find-java.ps1')

$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'TubeWinder.jar'
$dist = Join-Path $repo 'dist'
$staging = Join-Path (Join-Path $repo 'target') 'package-input'
$mainClass = 'com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp'

$candidates = @(Get-JavaCandidates)
$jdk = $candidates |
    Where-Object { $_.Major -ge $script:MinimumJava -and $_.HasPackager } |
    Select-Object -First 1

if (-not $jdk) {
    Write-Host "No JDK $script:MinimumJava or newer with jpackage was found."
    Write-Host 'Java installations found:'
    Write-Host (Format-JavaCandidates $candidates)
    Write-Host ''
    Write-Host 'Install a full JDK and try again:'
    Write-Host '    winget install --id EclipseAdoptium.Temurin.21.JDK -e'
    exit 1
}

# Make sure the jar is current before wrapping a runtime around it.
& (Join-Path $PSScriptRoot 'launch.ps1') -NoLaunch
if (-not (Test-Path $jar)) {
    Write-Error "TubeWinder.jar was not built."
}

# jpackage copies everything in --input, so stage just the jar.
if (Test-Path $staging) {
    Remove-Item $staging -Recurse -Force
}
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item $jar $staging

if (Test-Path (Join-Path $dist 'TubeWinder')) {
    Remove-Item (Join-Path $dist 'TubeWinder') -Recurse -Force
}

Write-Host 'Packaging (this takes a minute and the result is a few hundred MB)...'
& (Join-Path $jdk.Path "jpackage$script:ExeSuffix") `
    --type app-image `
    --name 'TubeWinder' `
    --input $staging `
    --main-jar 'TubeWinder.jar' `
    --main-class $mainClass `
    --app-version '0.1.0' `
    --vendor 'Berkeley Bike Builders' `
    --dest $dist

if ($LASTEXITCODE -ne 0) {
    Write-Error 'jpackage failed.'
}

Write-Host ''
Write-Host "Built $(Join-Path $dist 'TubeWinder')"
Write-Host 'Copy that whole folder anywhere and run TubeWinder.exe inside it. No Java needed.'
