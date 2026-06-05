# _jdk.ps1 - locate a JDK and expose $JdkHome / $Javac / $Java to the caller (dot-source this).
# Resolution order: $env:JAVA_HOME -> a JDK 17 under ~/.jdks -> any JDK under ~/.jdks.
$ErrorActionPreference = "Stop"

function Find-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { return $env:JAVA_HOME }
    $root = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $root) {
        $cands = Get-ChildItem $root -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') }
        $j17 = $cands | Where-Object { $_.Name -match '17' } | Select-Object -First 1
        if ($j17) { return $j17.FullName }
        $any = $cands | Select-Object -First 1
        if ($any) { return $any.FullName }
    }
    throw "No JDK found. Install one, e.g.: winget install --id EclipseAdoptium.Temurin.17.JDK -e"
}

$JdkHome = Find-JdkHome
$Javac   = Join-Path $JdkHome 'bin\javac.exe'
$Java    = Join-Path $JdkHome 'bin\java.exe'
