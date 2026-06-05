# WikiRMI — Design Specification

**Project:** Rozproszony System Wiki (WikiRMI)
**Course:** Programowanie Współbieżne i Rozproszone (2026), Temat 9 — System typu wiki
**Date:** 2026-06-05
**Status:** Approved design — ready for implementation plan

---

## 1. Goal & scope (Cel i zakres)

A distributed wiki application in Java using **RMI** for client–server communication and
**parallel-programming mechanisms** for safe concurrent access. An **administrator** creates the
page skeleton and the user accounts; **users** browse, search, and modify pages. The defining
technical challenge — and the bulk of the grade — is **correct, deadlock-free, fine-grained
concurrency** when multiple clients act on the same resource at once.

The system must demonstrably prevent race conditions (e.g. two users editing the same page
simultaneously), survive a server restart (persistent data), reclaim abandoned edit-locks via a
background daemon, and expose a clean separation between the remote contract, the server
implementation, and the GUI client.

### Locked decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Build scope | Full application **+** Polish PDF report |
| Client UI | **Swing GUI** (desktop) |
| Persistence | **JSON file** (human-readable, hand-rolled writer, no dependency) |
| Concurrency model | **Edit-lock (lease) + per-page `ReentrantReadWriteLock` + background reaper daemon** |
| Optional features | Live RMI callbacks, full-text search, password hashing, edit history — **all included** |
| Report | **Polish, PDF** |
| JDK | **Temurin/OpenJDK 17 LTS** (default; Java 8 also acceptable) |
| Program name | **WikiRMI** (default) |
| Build tooling | Plain `javac`/`java` + PowerShell scripts (no Maven/Gradle) |
| RMI registry | Started **in-process** via `LocateRegistry.createRegistry(1099)` |

### Inputs still needed from the user (collected before finalizing the report)

- Author name(s) and team membership.
- Division of work between team members (zadaniowy podział pracy) for the metryczka.
- Confirmation of JDK version (17 vs 8) and program name if they wish to override the defaults.

---

## 2. Architecture overview

Three-tier RMI system with a strict contract/server/client separation:

```
┌──────────────────┐    RMI    ┌──────────────┐          ┌────────────────────────┐
│  Swing Client     │◄────────►│ RMI Registry │◄─ bind ──│   Wiki Server          │
│                   │  lookup  │  (port 1099) │          │  WikiServiceImpl       │
│  gui/  (Swing)    │          └──────────────┘          │   ├─ WikiStore (core)  │
│     │             │                                    │   ├─ SessionManager    │
│  WikiClientController (only RMI caller)                │   ├─ NotificationSvc   │
│     │             │                                    │   └─ LockReaperDaemon  │
│  ClientCallbackImpl ◄──── server→client push ──────────┤  (UnicastRemoteObject) │
└──────────────────┘   (RMI callback, bidirectional)     └───────────┬────────────┘
                                                                      │ write-through
                                                                ┌─────▼──────┐
                                                                │ wiki.json   │  (atomic
                                                                └────────────┘    temp+rename)
```

The RMI registry runs inside the server process, so deployment is a single command. The report
will additionally document the standalone-`rmiregistry` variant for completeness.

---

## 3. Module / package structure

Single source tree, three top-level concerns. Package root: `wikirmi`.

```
src/
  wikirmi/common/                      # THE NETWORK CONTRACT — shared by client & server
    WikiService.java                   # remote interface (extends Remote)
    WikiClientCallback.java            # server→client remote interface (extends Remote)
    Role.java                          # enum { ADMIN, USER }
    dto/
      PageDTO.java                     # title, content, version, lastEditor, lastModified, lock
      PageSummaryDTO.java              # title, version, lockedBy, lastModified (lightweight lists)
      RevisionDTO.java                 # index, editor, timestamp, content
      UserDTO.java                     # username, role (NEVER carries password/hash)
      LockInfoDTO.java                 # holder username, acquiredAt, expiresAt, remainingMillis
      SessionDTO.java                  # token, UserDTO
    exceptions/
      WikiException.java               # base, Serializable, extends Exception
      AuthenticationException.java     # bad credentials / invalid-expired session
      AuthorizationException.java      # not permitted (e.g. non-admin calling admin op)
      PageLockedException.java         # carries holder + remaining seconds
      VersionConflictException.java    # carries current server version
      NotFoundException.java           # page/user not found
      ValidationException.java         # bad input (empty title, too long, duplicate, etc.)

  wikirmi/server/
    WikiServer.java                    # main(): create registry, build store, bind, start daemon, shutdown hook
    WikiServiceImpl.java               # implements WikiService (extends UnicastRemoteObject); thin auth+delegate layer
    model/
      Page.java                        # content, version, ReentrantReadWriteLock, EditLock, history list
      User.java                        # username, salted hash, salt, Role
      EditLock.java                    # holderToken, holderName, acquiredAt, expiresAt; isExpired(), heldBy()
    store/
      WikiStore.java                   # in-memory state + ALL concurrency control (the core)
      JsonPersistence.java             # snapshot↔JSON, atomic save (temp + ATOMIC_MOVE), load/seed
      Json.java                        # minimal hand-rolled JSON reader/writer (no dependency)
    auth/
      SessionManager.java              # token→Session map (ConcurrentHashMap), Semaphore for max clients
      PasswordHasher.java              # SHA-256 + per-user random salt; constant-time compare
    daemon/
      LockReaperDaemon.java            # daemon Thread: periodically reclaim expired edit-locks
    notify/
      NotificationService.java         # client callback registry + ExecutorService push; drops dead clients

  wikirmi/client/
    WikiClient.java                    # main(): connect, show LoginFrame
    ClientCallbackImpl.java            # implements WikiClientCallback (UnicastRemoteObject); forwards to GUI
    service/
      WikiClientController.java        # THE ONLY class that calls RMI; wraps stub + token; exception→result
    gui/
      LoginFrame.java                  # connect + login
      MainFrame.java                   # page list, search box, content viewer, admin menu
      EditDialog.java                  # acquires lock, edits, heartbeat timer, save/cancel
      HistoryDialog.java               # revision list + viewer
      AdminDialog.java                 # create/delete users & pages
      UiUtils.java                     # SwingWorker helpers, error dialogs, off-EDT call wrapper

  test/
    wikirmi/test/
      ConcurrencyTest.java             # race on edit-lock (the headline test)
      LostUpdateTest.java              # serialized saves, version increments exactly N
      ReadersWriterTest.java           # concurrent readers never see partial content
      LockExpiryTest.java              # daemon reclaims an un-renewed lock
      CreatePageRaceTest.java          # N threads same title → exactly one created
      RestartPersistenceTest.java      # save → reload store → state intact
      TestRunner.java                  # runs all, prints PASS/FAIL summary
```

**Separation rationale (grading):** `common/` is the contract both sides compile against. The
Swing layer never imports anything from `server/` and never calls RMI — it goes through
`WikiClientController`. This is the presentation/business-logic split the rubric rewards.

---

## 4. Remote interface (`WikiService`)

All methods `throws RemoteException, WikiException`. Every method except `login` takes a session
`token` (first argument) which is validated server-side.

```java
public interface WikiService extends Remote {
    // --- Authentication ---
    SessionDTO login(String username, String password) throws RemoteException, WikiException;
    void logout(String token) throws RemoteException, WikiException;

    // --- Admin only (Administrator tworzy szkielet strony i użytkowników) ---
    void createUser(String token, String username, String password, Role role) throws RemoteException, WikiException;
    void deleteUser(String token, String username) throws RemoteException, WikiException;
    List<UserDTO> listUsers(String token) throws RemoteException, WikiException;
    void createPage(String token, String title, String initialContent) throws RemoteException, WikiException;
    void deletePage(String token, String title) throws RemoteException, WikiException;

    // --- Browse / search (any authenticated user) ---
    List<PageSummaryDTO> listPages(String token) throws RemoteException, WikiException;
    List<PageSummaryDTO> searchPages(String token, String query) throws RemoteException, WikiException; // title+body
    PageDTO getPage(String token, String title) throws RemoteException, WikiException;                  // shared read lock

    // --- Editing (lock-based) ---
    LockInfoDTO acquireEditLock(String token, String title) throws RemoteException, WikiException;       // PageLockedException if held
    void renewEditLock(String token, String title) throws RemoteException, WikiException;                // heartbeat / extend lease
    void releaseEditLock(String token, String title) throws RemoteException, WikiException;              // cancel without saving
    PageDTO savePage(String token, String title, String newContent, long baseVersion) throws RemoteException, WikiException;

    // --- History (edit history feature) ---
    List<RevisionDTO> getHistory(String token, String title) throws RemoteException, WikiException;
    PageDTO getRevision(String token, String title, int revisionIndex) throws RemoteException, WikiException;

    // --- Notifications (RMI callback feature) ---
    void subscribe(String token, WikiClientCallback client) throws RemoteException, WikiException;
    void unsubscribe(String token, WikiClientCallback client) throws RemoteException, WikiException;
}
```

```java
public interface WikiClientCallback extends Remote {
    void onPageCreated(PageSummaryDTO page) throws RemoteException;
    void onPageChanged(PageSummaryDTO page) throws RemoteException;
    void onPageDeleted(String title) throws RemoteException;
    void onLockChanged(String title, LockInfoDTO lock) throws RemoteException; // lock==null means released/expired
}
```

---

## 5. Concurrency model (the heart — 3 pts)

Two **deliberately separated** mechanisms. The distinction is the key thing each team member must
be able to explain at the defense.

### 5.1 Edit-lock (logical lease)

Represents *"user X is currently editing this page"*, held across human think-time (seconds to
minutes). It is **state**, not a Java monitor held across a network call:

```
Page.editLock : EditLock { holderToken, holderName, acquiredAt, expiresAt }   // null == free
```

- `acquireEditLock` is the critical section. Under the page's **write lock**, the server does a
  check-and-set: if `editLock` is null/expired/owned-by-caller it installs a fresh lease and
  returns `LockInfoDTO`; otherwise it throws `PageLockedException(holder, remainingSeconds)`.
- Lease length `LEASE_MS` (default 30 s). The edit dialog calls `renewEditLock` on a heartbeat
  (default every 10 s) while open.
- `savePage` requires the caller to currently hold the lease (else `AuthorizationException`),
  verifies `baseVersion == page.version` as an optimistic safety net (else
  `VersionConflictException`), then under the write lock applies content, increments `version`,
  appends a `RevisionDTO` to history, releases the lease, persists, and notifies subscribers.
- `releaseEditLock` frees the lease (cancel path).

**Race test outcome:** N threads calling `acquireEditLock` on the same page → exactly one wins
(the check-and-set is serialized by the write lock); the other N−1 receive `PageLockedException`.

### 5.2 `ReentrantReadWriteLock` per page (memory consistency, fine-grained)

Each `Page` owns its own `ReentrantReadWriteLock`, held only for the microseconds of an in-memory
read or mutation:

- **Reads** (`getPage`, `getHistory`, `getRevision`, lock-status reads) take the **read lock** →
  unlimited concurrent readers; viewing never blocks another viewer.
- **Writes** (`savePage` applying content, `acquireEditLock`/`releaseEditLock` mutating the lease
  state) take the **write lock** → exclusive; a reader never observes half-written content.

This is the fine-grained, per-record locking the rubric rewards: **the server is never globally
locked while one user reads a page.**

### 5.3 Map-level concurrency

`pages : ConcurrentHashMap<String,Page>` and `users : ConcurrentHashMap<String,User>`.
Create-if-absent uses atomic `compute`/`putIfAbsent`, so two admins creating the same title race
safely → one succeeds, the other gets `ValidationException("page already exists")`.

### 5.4 Deadlock-freedom argument (stated explicitly in report)

Every operation holds **at most one page's lock at a time** and never nests one page's lock inside
another's, nor holds a page lock while taking the map lock in a conflicting order. With no nested
multi-lock acquisition there is no lock-ordering cycle, hence **no deadlock is possible**. The
`ConcurrentHashMap` provides its own internal synchronization independent of page locks.

### 5.5 Background daemon (`LockReaperDaemon`)

A `Thread` with `setDaemon(true)`, period `REAP_MS` (default 5 s). Each tick it scans pages and, for
any whose `editLock` is non-null and expired, takes that page's write lock, clears the lease, and
fires `onLockChanged(title, null)` to subscribers. This is the spec's
*"czyszczenie przestarzałych rezerwacji wątkiem działającym w tle (daemon)"*. Because it is a daemon
thread it never blocks JVM shutdown.

### 5.6 Max concurrent clients (`Semaphore`)

`SessionManager` holds a `Semaphore(MAX_CLIENTS)` (default 50). `login` calls `tryAcquire()`; on
failure it throws `AuthenticationException("server full")`. `logout` and session expiry
`release()`. Answers *"maksymalna liczba jednoczesnych klientów"* and demonstrates a counting
semaphore.

### 5.7 Notification isolation

`NotificationService` keeps a concurrent registry of `WikiClientCallback` stubs and pushes events on
an `ExecutorService` (fixed pool). A slow or dead client therefore never blocks the editing thread;
a callback that throws `RemoteException` causes that subscriber to be removed.

---

## 6. Persistence & restart

- Single file `wiki.json` in the server working directory, human-readable.
- `Json.java`: a minimal hand-rolled reader/writer (objects, arrays, strings, numbers, booleans,
  null) — **no third-party dependency**.
- **Write-through:** every successful mutating operation (create/delete user, create/delete page,
  save) triggers `JsonPersistence.save(snapshot)`. A snapshot is taken under brief per-page read
  locks to avoid blocking writers for the whole serialization.
- **Crash safety:** write to `wiki.json.tmp`, `fsync`, then `Files.move(tmp, wiki.json,
  ATOMIC_MOVE, REPLACE_EXISTING)`. A crash mid-save leaves the previous good file intact.
- **Shutdown hook:** `Runtime.addShutdownHook` performs a final save.
- **Startup:** load `wiki.json` if present; otherwise seed a default `admin` account (password
  documented in the report / printed once on first run) and one sample page so the system is usable
  immediately. → answers *"jak aplikacja reaguje na restart serwera"*: full state restored.

---

## 7. Authentication, sessions, authorization

- `login` validates the password against the stored salted SHA-256 hash (constant-time compare),
  acquires a semaphore permit, creates a `Session{token=UUID, username, role, lastSeen}` in a
  `ConcurrentHashMap`, returns `SessionDTO`.
- Every other call resolves the token → `Session` (throws `AuthenticationException` if missing).
  Admin-only methods additionally require `role == ADMIN` (else `AuthorizationException`).
- Sessions may expire on inactivity (optional; the reaper can also release stale sessions + their
  semaphore permits). `logout` removes the session and releases the permit.
- `PasswordHasher`: per-user random salt (`SecureRandom`), `SHA-256(salt || password)`, stored as
  hex. `UserDTO` never carries the hash or salt over the wire.

---

## 8. Client design (presentation/logic separation)

- **`WikiClientController`** is the only class that touches the RMI stub. It holds the `WikiService`
  stub + current session token, exposes plain Java methods to the GUI, and translates
  `WikiException`/`RemoteException` into UI-friendly results/messages.
- All remote calls run **off the Swing EDT** via `SwingWorker`/the `UiUtils` wrapper, so the UI
  never freezes on a network call.
- `EditDialog` lifecycle: `acquireEditLock` on open → heartbeat `renewEditLock` via a `javax.swing.Timer`
  → `savePage` (or `releaseEditLock` on cancel/close). Live `onLockChanged`/`onPageChanged`
  callbacks update the page list and disable editing of pages locked by others.
- `RemoteException` (server down) surfaces as *"Utracono połączenie z serwerem"* and disables
  editing actions.

---

## 9. Error handling & input validation

Server-side validation feeds the *"odporność na błędy logiczne użytkownika"* criterion:

- Empty/blank or over-length titles, content, usernames → `ValidationException`.
- Duplicate username/page title → `ValidationException`.
- Unknown page/user → `NotFoundException`.
- Saving without holding the lock, or non-admin invoking an admin op → `AuthorizationException`.
- Stale `baseVersion` → `VersionConflictException` (carries current version so the client can offer
  reload).
- All are `Serializable` subclasses of `WikiException` and cross the wire intact; the controller maps
  each to a clear Polish dialog message.

---

## 10. Tests (concurrency proof for the report)

Headless `main`/runner classes using `CountDownLatch` start-gates so threads fire simultaneously.
Each prints a clear `PASS`/`FAIL` line; `TestRunner` aggregates.

1. **`ConcurrencyTest`** — 10+ threads call `acquireEditLock` on one page at once → assert exactly
   1 success and N−1 `PageLockedException`. *(headline required test)*
2. **`LostUpdateTest`** — N threads each acquire→save→release in turn → final `version == N`, no
   update lost, history length == N.
3. **`ReadersWriterTest`** — many concurrent readers + a writer → readers only ever observe a
   complete, consistent content/version pair (no torn reads); readers overlap in time.
4. **`LockExpiryTest`** — acquire a lock, never renew, wait > `LEASE_MS` + `REAP_MS` → daemon has
   cleared it; a second user can then acquire.
5. **`CreatePageRaceTest`** — N threads create the same title → exactly one succeeds.
6. **`RestartPersistenceTest`** — populate store, save, build a fresh store from the same file →
   pages/users/history identical (restart simulation).

Results (counts, timings) go into the report's *"Wyniki testów współbieżności"* section.

---

## 11. Toolchain, build & run

- **JDK:** install Temurin/OpenJDK 17 LTS (RMI + `rmiregistry` fully present; no `SecurityManager`
  needed because client and server both have `common/` on their classpath — no codebase download).
  Java 8 is an acceptable fallback.
- **No Maven/Gradle.** Compile with `javac` to an `out/` dir; run with `java`.
- **Scripts (PowerShell, Windows):**
  - `build.ps1` — compile `src` + `test` into `out/`.
  - `run-server.ps1` — start `WikiServer` (creates registry on 1099, binds service).
  - `run-client.ps1` — start `WikiClient` (one per client; launch several to demo concurrency).
  - `run-tests.ps1` — run `TestRunner` (the concurrency proof).
- Configurable constants (port, `MAX_CLIENTS`, `LEASE_MS`, `REAP_MS`, data-file path) centralized
  (e.g. `ServerConfig`) and documented.

---

## 12. Deliverables

1. Complete, runnable Java source (`src/`, `test/`) + the four PowerShell scripts.
2. Seed data / first-run behavior documented (default admin credentials).
3. **Polish PDF report** (section 13).

---

## 13. Report outline (Polish PDF — maps to the doc requirements)

1. **Metryczka** — tytuł, autorzy, zadaniowy podział pracy *(needs user input)*.
2. **Tytuł programu** — WikiRMI.
3. **Cel i zakres** — opis celu + przyjęte założenia.
4. **Architektura i struktura logiczna** — schemat blokowy Klient–Serwer–RMI Registry.
5. **Specyfikacja interfejsu zdalnego** — dokładny opis każdej metody `WikiService`.
6. **Model współbieżności i synchronizacji** — sekcje krytyczne (jednoczesna edycja tej samej
   strony), mechanizmy: `ReentrantReadWriteLock`, edit-lease, `ConcurrentHashMap`, `Semaphore`,
   `ExecutorService`, daemon; argument o braku zakleszczeń.
7. **Zarządzanie pamięcią i wydajność** — daemon czyszczący przeterminowane blokady, callbacki poza
   wątkiem edycji, blokowanie drobnoziarniste, persistencja write-through.
8. **Instrukcja wdrożenia i obsługi** — krok po kroku: instalacja JDK, kompilacja, uruchomienie
   registry/serwera/klienta.
9. **Ograniczenia i testy poprawności** — maksymalna liczba klientów, scenariusze i wyniki testów
   współbieżności, sposób przechowywania danych i reakcja na restart serwera.

---

## 14. Defaults applied (override anytime)

- JDK **17** (not 8).
- Program name **WikiRMI**.
- Lease 30 s, heartbeat 10 s, reaper 5 s, max clients 50, port 1099 — all configurable.
