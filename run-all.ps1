# run-all.ps1 - uruchamia serwer + N klientów, każde w OSOBNYM oknie (nic nie blokuje terminala).
# Użycie:   .\run-all.ps1            (1 serwer + 2 klientów)
#           .\run-all.ps1 -Clients 3 (1 serwer + 3 klientów - do demo współbieżności)
param([int]$Clients = 2)
. "$PSScriptRoot\_jdk.ps1"

# 1) Serwer we własnym oknie konsoli (widać logi; Ctrl+C w tym oknie zatrzymuje serwer).
Start-Process -FilePath $Java -ArgumentList '-Dfile.encoding=UTF-8','-cp',$ClassPath,'wikirmi.server.WikiServer'
Write-Host "Serwer startuje na porcie 1099..."

# 2) Czekamy aż rejestr RMI zacznie nasłuchiwać (max ~6 s).
for ($i = 0; $i -lt 20; $i++) {
    if ((Test-NetConnection localhost -Port 1099 -WarningAction SilentlyContinue).TcpTestSucceeded) { break }
    Start-Sleep -Milliseconds 300
}
Write-Host "Serwer gotowy."

# 3) Klienci - każdy jako osobne okno GUI.
for ($c = 1; $c -le $Clients; $c++) {
    Start-Process -FilePath $Java -ArgumentList '-Dfile.encoding=UTF-8','-cp',$ClassPath,'wikirmi.client.WikiClient'
    Write-Host "Klient $c uruchomiony."
}
Write-Host ""
Write-Host "Gotowe. Zaloguj sie w oknach klienta: admin / admin123"
