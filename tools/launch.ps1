# Launch Tube Winder, building the jar first if it is missing or out of date.
#
# You are not meant to run this directly. Double-click TubeWinder.bat at the top of the repo, or
# make a desktop shortcut with tools\create-desktop-shortcut.ps1.
#
# Two things this has to get right:
#
#   1. Pick a Java that can actually run the app. Windows usually has an old Java 8 stub in
#      System32 that sits at the front of PATH, and running a Java 17 jar on it fails with a
#      "you need to update Java" message. So every candidate is version-checked before use, and
#      anything below 17 is skipped rather than trusted because it came first.
#
#   2. Never run stale code. If any .java file is newer than the jar, it recompiles first, so
#      pulling changes and double-clicking is all there is to it.

[CmdletBinding()]
param(
    # Build if needed but do not start the app. Used for testing.
    [switch]$NoLaunch,

    # Print what Java runtimes were found and what was chosen, then exit.
    [switch]$Diagnose
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'find-java.ps1')

$repo = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repo 'TubeWinder.jar'
$classes = Join-Path (Join-Path $repo 'target') 'classes'
$mainClass = 'com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp'
$sourceRoots = @(
    (Join-Path (Join-Path $repo 'backend') 'src'),
    (Join-Path (Join-Path $repo 'ui') 'src')
)

function Show-Problem([string]$message) {
    # The desktop shortcut runs hidden, so a console message would go unseen.
    try {
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.MessageBox]::Show($message, 'Tube Winder', 'OK', 'Error') | Out-Null
    } catch {
        Write-Host $message -ForegroundColor Red
    }
    exit 1
}

$candidates = @(Get-JavaCandidates)
$usable = @($candidates | Where-Object { $_.Major -ge $script:MinimumJava })

# A JDK is better than a runtime-only install, because it can rebuild after a pull.
$jdk = $usable | Where-Object { $_.HasCompiler } | Select-Object -First 1
$runtime = if ($jdk) { $jdk } else { $usable | Select-Object -First 1 }

if ($Diagnose) {
    Write-Host "Looking for Java $script:MinimumJava or newer."
    if (-not $candidates) {
        Write-Host '  nothing found'
    }
    foreach ($c in $candidates) {
        $note = if ($c.Major -lt $script:MinimumJava) { 'too old' }
                elseif ($c.HasCompiler) { 'usable, has compiler' }
                else { 'usable, runtime only' }
        Write-Host ("  Java {0,-3} {1,-22} {2}" -f $c.Major, $note, $c.Path)
    }
    Write-Host ''
    Write-Host ("Chosen: " + $(if ($runtime) { $runtime.Path } else { 'none' }))
    exit 0
}

# --------------------------------------------------------------------- build

function Test-BuildNeeded {
    if (-not (Test-Path $jar)) {
        return $true
    }
    $jarTime = (Get-Item $jar).LastWriteTimeUtc
    foreach ($root in $sourceRoots) {
        if (-not (Test-Path $root)) {
            continue
        }
        $newer = Get-ChildItem -Path $root -Recurse -Filter *.java -File |
            Where-Object { $_.LastWriteTimeUtc -gt $jarTime } |
            Select-Object -First 1
        if ($newer) {
            return $true
        }
    }
    return $false
}

function Build-Jar {
    if (-not $jdk) {
        if (Test-Path $jar) {
            Write-Host 'No JDK found; launching the existing jar without rebuilding.'
            return
        }
        Show-Problem (
            "Tube Winder needs a JDK $script:MinimumJava or newer to build itself the first time.`n`n" +
            "Java installations found:`n" + (Format-JavaCandidates $candidates) + "`n`n" +
            "Install one:`n    winget install --id EclipseAdoptium.Temurin.21.JDK -e`n`n" +
            'Then double-click TubeWinder.bat again.')
    }

    Write-Host 'Building Tube Winder...'
    if (Test-Path $classes) {
        Remove-Item $classes -Recurse -Force
    }
    New-Item -ItemType Directory -Path $classes -Force | Out-Null

    $files = Get-ChildItem -Path $sourceRoots -Recurse -Filter *.java -File |
        Select-Object -ExpandProperty FullName
    if (-not $files) {
        Show-Problem "No Java sources found under $repo. Is this the right folder?"
    }

    & (Join-Path $jdk.Path "javac$script:ExeSuffix") -nowarn -d $classes --release $script:MinimumJava @files
    if ($LASTEXITCODE -ne 0) {
        Show-Problem 'Compilation failed. Run ./tools/build-jar.sh from Git Bash to see the errors.'
    }

    & (Join-Path $jdk.Path "jar$script:ExeSuffix") --create --file $jar --main-class $mainClass -C $classes .
    if ($LASTEXITCODE -ne 0) {
        Show-Problem 'Packaging the jar failed.'
    }
    Write-Host "Built $jar"
}

if (Test-BuildNeeded) {
    Build-Jar
}

if ($NoLaunch) {
    Write-Host 'Built; not launching (-NoLaunch).'
    exit 0
}

# -------------------------------------------------------------------- launch

if (-not $runtime) {
    Show-Problem (
        "No Java $script:MinimumJava or newer was found, so Tube Winder cannot start.`n`n" +
        "Java installations found:`n" + (Format-JavaCandidates $candidates) + "`n`n" +
        "Install a current one:`n    winget install --id EclipseAdoptium.Temurin.21.JDK -e")
}

# javaw opens the window with no console behind it; plain java is the fallback off Windows.
$runner = Join-Path $runtime.Path "javaw$script:ExeSuffix"
if (-not (Test-Path $runner)) {
    $runner = Join-Path $runtime.Path "java$script:ExeSuffix"
}

Start-Process -FilePath $runner -ArgumentList @('-jar', $jar) -WorkingDirectory $repo
