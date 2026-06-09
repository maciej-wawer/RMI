# run-client.ps1 - start a WikiRMI Swing client. Launch several for a concurrency demo.
# Optional args: <host> <port>  (default: localhost 1099)
. "$PSScriptRoot\_jdk.ps1"
& $Java "-Dfile.encoding=UTF-8" -cp $ClassPath wikirmi.client.WikiClient @args
