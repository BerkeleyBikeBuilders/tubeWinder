# Build the Windows installer for Tube Winder.
#
#     powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1
#
# Produces dist\TubeWinder-<version>.exe. Running that installs the app, drops a desktop
# shortcut and a Start menu entry, and registers an uninstaller in Add or Remove Programs.
# A Java runtime is bundled inside, so the machine needs nothing installed.
#
# UGS itself does not install: its download is a portable folder you extract and run. This goes a
# step further, because a shortcut that is always there is easier for people who are not going to
# remember where they unzipped something. Pass -Type portable for the UGS-style folder instead.
#
# REQUIREMENTS
#   - A full JDK 17 or newer, with jpackage. The runtime inside IntelliJ usually has no jpackage.
#         winget install --id EclipseAdoptium.Temurin.21.JDK -e
#   - Inno Setup 6, which jpackage drives to build the installer. Not needed for -Type portable.
#         winget install --id JRSoftware.InnoSetup -e
#
# Must run on Windows: jpackage only builds for the platform it runs on.

param(
    # installer = dist\TubeWinder-<version>.exe, installs with shortcuts.
    # portable  = dist\TubeWinder\TubeWinder.exe, extract-and-run, the way UGS ships.
    [ValidateSet('installer', 'portable')]
    [string]$Type = 'installer',

    [string]$AppVersion = '0.1.0'
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'find-java.ps1')

$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'TubeWinder.jar'
$icon = Join-Path $PSScriptRoot 'TubeWinder.ico'
$dist = Join-Path $repo 'dist'
$staging = Join-Path (Join-Path $repo 'target') 'package-input'
$mainClass = 'com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp'

# Fixed so that installing a new version upgrades the old one in place. Change this and every
# version installs side by side, each with its own entry in Add or Remove Programs.
$upgradeUuid = 'def0743f-1686-40e1-bdb2-b6a3ab756e69'

# ------------------------------------------------------------------ toolchain

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

if ($Type -eq 'installer') {
    # jpackage shells out to Inno Setup for --type exe, and its own error if it is missing is
    # cryptic. Check first and say something useful.
    $inno = Get-Command 'iscc.exe' -ErrorAction SilentlyContinue
    if (-not $inno) {
        $guesses = Get-ChildItem 'C:\Program Files (x86)\Inno Setup*\ISCC.exe',
                                 'C:\Program Files\Inno Setup*\ISCC.exe' `
                                 -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($guesses) {
            # jpackage looks on PATH, so put it there for this process only.
            $env:PATH = (Split-Path -Parent $guesses.FullName) + ';' + $env:PATH
            Write-Host "Using Inno Setup at $($guesses.FullName)"
        } else {
            Write-Host 'Inno Setup 6 is needed to build the installer, and was not found.'
            Write-Host ''
            Write-Host '    winget install --id JRSoftware.InnoSetup -e'
            Write-Host ''
            Write-Host 'Then open a new terminal and run this again.'
            Write-Host 'Or build the portable folder instead, which needs no extra tools:'
            Write-Host '    powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1 -Type portable'
            exit 1
        }
    }
}

# ---------------------------------------------------------------------- build

# Make sure the jar is current before wrapping a runtime around it.
& (Join-Path $PSScriptRoot 'launch.ps1') -NoLaunch
if (-not (Test-Path $jar)) {
    Write-Error 'TubeWinder.jar was not built.'
}

# jpackage copies everything in --input, so stage just the jar.
if (Test-Path $staging) {
    Remove-Item $staging -Recurse -Force
}
New-Item -ItemType Directory -Path $staging -Force | Out-Null
Copy-Item $jar $staging

New-Item -ItemType Directory -Path $dist -Force | Out-Null

$common = @(
    '--name', 'TubeWinder'
    '--input', $staging
    '--main-jar', 'TubeWinder.jar'
    '--main-class', $mainClass
    '--app-version', $AppVersion
    '--vendor', 'Bike Builders of Berkeley'
    '--description', 'G-code slicer and control software for the Bike Builders filament winder'
    '--copyright', 'GPL v3'
    '--dest', $dist
)
if (Test-Path $icon) {
    $common += @('--icon', $icon)
} else {
    Write-Host "No icon at $icon; the app will get the default Java icon."
}

if ($Type -eq 'portable') {
    $target = Join-Path $dist 'TubeWinder'
    if (Test-Path $target) {
        Remove-Item $target -Recurse -Force
    }
    Write-Host 'Building the portable folder (a minute or so, a few hundred MB)...'
    & (Join-Path $jdk.Path "jpackage$script:ExeSuffix") @common --type app-image
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'jpackage failed.'
    }
    Write-Host ''
    Write-Host "Built $target"
    Write-Host 'Copy that whole folder anywhere and run TubeWinder.exe inside it.'
    exit 0
}

Write-Host 'Building the installer (a minute or so, a few hundred MB)...'
& (Join-Path $jdk.Path "jpackage$script:ExeSuffix") @common `
    --type exe `
    --win-shortcut `
    --win-menu `
    --win-menu-group 'Bike Builders' `
    --win-dir-chooser `
    --win-per-user-install `
    --win-upgrade-uuid $upgradeUuid

if ($LASTEXITCODE -ne 0) {
    Write-Error 'jpackage failed.'
}

$built = Get-ChildItem (Join-Path $dist '*.exe') |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

Write-Host ''
if ($built) {
    $size = [math]::Round($built.Length / 1MB)
    Write-Host "Built $($built.FullName) ($size MB)"
} else {
    Write-Host "Built into $dist"
}
Write-Host 'Run it to install. It creates a desktop shortcut and a Start menu entry under'
Write-Host 'Bike Builders, and installs per user so Windows does not ask for an admin password.'
