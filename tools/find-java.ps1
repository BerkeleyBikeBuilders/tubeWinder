# Locating a usable Java. Dot-sourced by launch.ps1 and package-app.ps1.
#
# The reason this is careful rather than "whatever is on PATH": Windows usually keeps an old Java 8
# launcher in System32, at the front of PATH, left behind by some installer years ago. Trusting it
# means the app fails to start with a "you need to update Java" message even though a perfectly good
# JDK 21 is sitting in the JetBrains folder. So every candidate is asked its version and anything
# too old is skipped.

$script:MinimumJava = 17

$script:OnWindows = ($null -eq $IsWindows) -or $IsWindows
$script:ExeSuffix = if ($script:OnWindows) { '.exe' } else { '' }

# Every bin directory that holds a java executable.
function Get-JavaBinDirectories {
    $dirs = @()

    if ($env:JAVA_HOME) {
        $dirs += (Join-Path $env:JAVA_HOME 'bin')
    }

    $dirs += (Get-Command "java$script:ExeSuffix" -All -ErrorAction SilentlyContinue |
        ForEach-Object { Split-Path -Parent $_.Source })

    if ($script:OnWindows) {
        foreach ($pattern in @(
                "C:\Program Files\JetBrains\*\jbr\bin",
                "C:\Program Files\Eclipse Adoptium\*\bin",
                "C:\Program Files\Java\*\bin",
                "C:\Program Files\Microsoft\jdk*\bin",
                "C:\Program Files\Amazon Corretto\*\bin",
                "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\*\bin")) {
            $dirs += (Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty FullName)
        }
    }

    $dirs |
        Where-Object { $_ -and (Test-Path (Join-Path $_ "java$script:ExeSuffix")) } |
        Select-Object -Unique
}

# Major version of the runtime in a bin directory, or 0 if it cannot be determined.
# javaw writes nothing to the console, so the sibling java is what gets asked.
function Get-JavaMajor([string]$binDir) {
    $java = Join-Path $binDir "java$script:ExeSuffix"
    try {
        $output = (& $java -version 2>&1 | Out-String)
    } catch {
        return 0
    }
    if ($output -match 'version "(\d+)(?:\.(\d+))?') {
        $major = [int]$Matches[1]
        # Java 8 and earlier report themselves as 1.8.0_xxx.
        if ($major -eq 1 -and $Matches[2]) {
            $major = [int]$Matches[2]
        }
        return $major
    }
    return 0
}

function Get-JavaCandidates {
    foreach ($dir in Get-JavaBinDirectories) {
        [pscustomobject]@{
            Path = $dir
            Major = (Get-JavaMajor $dir)
            HasCompiler = (Test-Path (Join-Path $dir "javac$script:ExeSuffix"))
            HasPackager = (Test-Path (Join-Path $dir "jpackage$script:ExeSuffix"))
        }
    }
}

function Format-JavaCandidates($candidates) {
    if (-not $candidates) {
        return '  (none found)'
    }
    ($candidates | ForEach-Object { "  Java $($_.Major): $($_.Path)" }) -join "`n"
}
