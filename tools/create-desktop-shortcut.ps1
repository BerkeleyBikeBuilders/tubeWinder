# Put a "Tube Winder" shortcut on the desktop.
#
# Run once, from PowerShell in the repo:
#     powershell -ExecutionPolicy Bypass -File .\tools\create-desktop-shortcut.ps1
#
# The shortcut runs tools\launch.ps1 with a hidden window, so it builds when it needs to and opens
# the app without a console appearing.

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $PSScriptRoot 'launch.ps1'

if (-not (Test-Path $launcher)) {
    Write-Error "launch.ps1 not found at $launcher"
}

$powershell = (Get-Command powershell.exe -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Source)
if (-not $powershell) {
    $powershell = (Get-Command pwsh.exe -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty Source)
}
if (-not $powershell) {
    Write-Error 'Could not find powershell.exe.'
}

$desktop = [Environment]::GetFolderPath('Desktop')
$linkPath = Join-Path $desktop 'Tube Winder.lnk'

$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($linkPath)
$link.TargetPath = $powershell
$link.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$launcher`""
$link.WorkingDirectory = $repo
$link.WindowStyle = 7
$link.Description = 'Berkeley Bike Builders tube winder'
$link.Save()

Write-Host "Created $linkPath"
