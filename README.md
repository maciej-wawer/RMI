# WikiRMI — Rozproszony System Wiki

Aplikacja rozproszona w języku **Java (RMI)** realizująca system typu wiki: **administrator** tworzy
szkielety stron oraz konta użytkowników, a **użytkownicy** przeglądają, wyszukują i modyfikują strony.
Rdzeniem projektu jest bezpieczna, drobnoziarnista i wolna od zakleszczeń współbieżność przy
jednoczesnej edycji tych samych zasobów przez wielu klientów.

## Wymagania

- **JDK 17** (testowano na Amazon Corretto 17). Java 8 również jest wspierana.
  Skrypty automatycznie wyszukują JDK w `%USERPROFILE%\.jdks` lub w `JAVA_HOME`.
- **Windows + PowerShell** (dostarczone skrypty `.ps1`).

## Budowanie

```powershell
.\build.ps1
```

Kompiluje `src/` i `test/` do katalogu `out/` (z kodowaniem UTF-8).

## Uruchomienie

1. **Serwer** (tworzy rejestr RMI na porcie 1099 w tym samym procesie, wczytuje/seeduje dane):

   ```powershell
   .\run-server.ps1
   ```

   Przy pierwszym uruchomieniu tworzony jest plik `wiki.json` z domyślnym kontem administratora
   i przykładową stroną.

2. **Klient** (okno Swing) — uruchom **wiele instancji**, aby zademonstrować współbieżność:

   ```powershell
   .\run-client.ps1
   ```

**Domyślne konto administratora:** `admin` / `admin123`.

## Testy

Zestaw testów współbieżności i poprawności (dowód braku stanu wyścigu):

```powershell
.\run-tests.ps1
```

Oczekiwany wynik: `12 passed, 0 failed, 0 skipped`, w tym kluczowy `ConcurrencyTest`
(10 wątków → dokładnie 1 zdobywa blokadę, 9 odrzuconych).

Dodatkowe testy uruchamiane samodzielnie (wymagają rejestru RMI / ekranu):

```powershell
java -cp out wikirmi.test.RmiSmoke              # round-trip przez RMI
java -cp out wikirmi.test.RmiIntegrationTest    # dwóch klientów: wyścig przez sieć + powiadomienia
java -cp out wikirmi.test.GuiConstructionSmoke  # budowa wszystkich okien GUI
```

## Architektura

| Warstwa | Pakiet | Odpowiedzialność |
|---|---|---|
| Kontrakt | `wikirmi.common` | interfejsy zdalne (`WikiService`, `WikiClientCallback`), DTO (`Serializable`), wyjątki |
| Serwer | `wikirmi.server` | `WikiServiceImpl`, `WikiStore` (rdzeń współbieżności), persystencja JSON, sesje, daemon, powiadomienia |
| Klient | `wikirmi.client` | GUI Swing + `WikiClientController` (jedyny punkt styku z RMI) |

## Model współbieżności (skrót)

- **Blokada edycji (dzierżawa)** — logiczny stan „użytkownik X edytuje stronę”, ustawiany metodą
  *sprawdź-i-ustaw* pod blokadą zapisu danej strony: z N jednoczesnych prób **dokładnie jedna** wygrywa,
  pozostałe otrzymują `PageLockedException`.
- **`ReentrantReadWriteLock` na każdą stronę** — wielu czytelników naraz, wyłączny zapis; serwer nigdy
  nie jest globalnie blokowany na czas odczytu.
- **`ConcurrentHashMap` + `putIfAbsent`** — atomowe tworzenie stron/użytkowników.
- **Daemon** (`LockReaperDaemon`) — w tle zwalnia przeterminowane blokady (np. po awarii klienta).
- **`Semaphore`** — limit jednoczesnych klientów (domyślnie 50).
- **`ExecutorService`** — powiadomienia (callbacki RMI) wysyłane poza wątkiem edycji; martwi klienci są usuwani.
- **Brak zakleszczeń** — każda operacja trzyma co najwyżej jedną blokadę strony naraz (brak zagnieżdżeń → brak cyklu).

## Dane i restart

Stan zapisywany jest do czytelnego `wiki.json` (write-through po każdej zmianie + hak zamknięcia),
zapis atomowy (plik tymczasowy + `ATOMIC_MOVE`). Po restarcie serwera dane są w pełni odtwarzane.
Blokady edycji są stanem ulotnym i celowo nie są utrwalane.
