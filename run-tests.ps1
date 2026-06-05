# run-tests.ps1 - run the concurrency/persistence test suite (the race-condition proof).
. "$PSScriptRoot\_jdk.ps1"
& $Java "-Dfile.encoding=UTF-8" -cp (Join-Path $PSScriptRoot 'out') wikirmi.test.TestRunner @args
exit $LASTEXITCODE
