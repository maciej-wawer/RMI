# _jdk.ps1 - locate a JDK and expose $JdkHome / $Javac / $Java to the caller (dot-source this).
# Resolution order: $env:JAVA_HOME -> a JDK 17 under ~/.jdks -> any JDK under ~/.jdks.
$ErrorActionPreference = "Stop"

function Find-JdkHome {
    if ($env:JAVA_HOME) {
        $javacPath = if ($IsWindows) { Join-Path $env:JAVA_HOME 'bin\javac.exe' } else { Join-Path $env:JAVA_HOME 'bin/javac' }
        if (Test-Path $javacPath) { return $env:JAVA_HOME }
    }

    $homeDir = $env:USERPROFILE
    if (-not $homeDir) { $homeDir = $env:HOME }
    if (-not $homeDir) {
        throw "Cannot determine user home directory. Set USERPROFILE (Windows) or HOME (Linux/macOS)."
    }

    $root = Join-Path $homeDir '.jdks'
    if (Test-Path $root) {
        $javacRelative = if ($IsWindows) { 'bin\javac.exe' } else { 'bin/javac' }
        $cands = Get-ChildItem $root -Directory | Where-Object { Test-Path (Join-Path $_.FullName $javacRelative) }
        $j17 = $cands | Where-Object { $_.Name -match '17' } | Select-Object -First 1
        if ($j17) { return $j17.FullName }
        $any = $cands | Select-Object -First 1
        if ($any) { return $any.FullName }
    }
    throw "No JDK found. Install one, e.g.: winget install --id EclipseAdoptium.Temurin.17.JDK -e"
}

$JdkHome = Find-JdkHome
$Javac   = if ($IsWindows) { Join-Path $JdkHome 'bin\javac.exe' } else { Join-Path $JdkHome 'bin/javac' }
$Java    = if ($IsWindows) { Join-Path $JdkHome 'bin\java.exe' } else { Join-Path $JdkHome 'bin/java' }
