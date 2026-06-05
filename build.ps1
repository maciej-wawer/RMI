# build.ps1 - compile all Java sources (src + test) into out/. UTF-8 so Polish text compiles correctly.
. "$PSScriptRoot\_jdk.ps1"
Write-Host "Using JDK: $JdkHome"

$out = Join-Path $PSScriptRoot 'out'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null

$srcDirs = @((Join-Path $PSScriptRoot 'src'), (Join-Path $PSScriptRoot 'test')) | Where-Object { Test-Path $_ }
$files = @(Get-ChildItem -Recurse -Filter *.java -Path $srcDirs | ForEach-Object { $_.FullName })
if ($files.Count -eq 0) { throw "No .java files found under src/ or test/." }

$javacArgs = @('-encoding', 'UTF-8', '-d', $out) + $files
& $Javac @javacArgs
if ($LASTEXITCODE -ne 0) { throw "Compilation FAILED (exit $LASTEXITCODE)" }
Write-Host ("Build OK -> out/  ({0} files)" -f $files.Count)
