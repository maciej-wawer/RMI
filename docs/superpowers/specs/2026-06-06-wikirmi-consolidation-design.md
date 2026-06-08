# WikiRMI — Konsolidacja i dosadna współbieżność (spec)

**Data:** 2026-06-06 · **Status:** zatwierdzone.
**Cel:** ten sam działający projekt (RMI + Swing), ale współbieżność pokazana DOSADNIE i czytelnie,
mniej plików, dużo polskich komentarzy „po studencku". Wszystkie 4 mechanizmy użyte i nazwane wprost.

## Zasady
- Aplikacja działa bez zmian funkcjonalnych; po każdym kroku `build` + testy (utrzymujemy 17/17).
- Proste konstrukcje, polskie komentarze wyjaśniające „dlaczego".

## Cztery mechanizmy — gdzie (po scaleniu)
W jednym pliku `WikiStore.java` (rdzeń serwera) — 4 sekcje z wielkimi nagłówkami:
- **BLOKADY** — `ReentrantReadWriteLock` per strona (edycja: czytelnicy/pisarze).
- **MONITORY** — `synchronized` na wspólnym liczniku statystyk (np. liczba zapisów/odczytów) — realne użycie.
- **SEMAFORY** — `Semaphore` limit jednoczesnych klientów (wciągnięte z `SessionManager`).
- **WĄTKI** — wątek `daemon` czyszczący przeterminowane blokady (wciągnięte z `LockReaperDaemon`).

## Scalenia (mniej plików, ~31 → ~19 w src)
- `SessionManager` + `LockReaperDaemon` → wciągnięte do `WikiStore` (sesje+semafor, wątek daemon).
- 6 plików DTO → 1 `common/Dto.java` (klasy zagnieżdżone: `Dto.Page`, `Dto.PageSummary`, `Dto.Revision`,
  `Dto.User`, `Dto.Lock`, `Dto.Session`).
- 7 wyjątków → 2 pliki: `WikiException` (ogólny) + `PageLockedException` (kluczowy dla demo). Pozostałe
  typy upraszczam do `WikiException` z komunikatem; sygnatury `throws WikiException`.

## Bez zmian
Interfejs RMI (`WikiService`, `WikiClientCallback`), GUI Swing, persystencja JSON (`Json`,
`JsonPersistence`), `PasswordHasher`, `Page`, `User`, `Clock`, `ServerConfig`, `WikiServer`, `WikiServiceImpl`
(aktualizacja referencji), `WikiClient`.

## Kolejność wykonania (każdy krok: build + testy + commit)
1. Scalenie wyjątków (7 → 2).
2. Scalenie DTO (6 → 1).
3. Wciągnięcie `SessionManager` + `LockReaperDaemon` do `WikiStore`; dodanie MONITORA (synchronized);
   4 sekcje z nagłówkami; dużo komentarzy. Aktualizacja `WikiServiceImpl`, `WikiServer`, testów.
4. Aktualizacja README + raportu (struktura plików), regeneracja PDF.
5. Weryfikacja: testy 17/17, GUI smoke, integracja RMI; push.
