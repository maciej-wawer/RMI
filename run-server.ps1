# run-server.ps1 - start the WikiRMI server (creates the RMI registry in-process on port 1099).
. "$PSScriptRoot\_jdk.ps1"
& $Java "-Dfile.encoding=UTF-8" -cp $ClassPath wikirmi.server.WikiServer @args
