package wikirmi.server.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import wikirmi.common.Role;
import wikirmi.common.dto.*;
import wikirmi.common.exceptions.*;
import wikirmi.server.model.*;

/**
 * In-memory state plus ALL concurrency control for the wiki.
 *
 * <p>Two cooperating mechanisms:
 * <ul>
 *   <li><b>Edit-lease</b> ({@link Page#editLock()}): a logical "user X is editing this page"
 *       marker held across think-time. Acquiring/saving check-and-set it under the page write lock,
 *       so of N racing editors exactly one wins.</li>
 *   <li><b>Per-page {@link java.util.concurrent.locks.ReentrantReadWriteLock}</b>: held only for the
 *       microseconds of an in-memory read/mutation. Many concurrent readers; exclusive writers; no
 *       reader ever sees a half-written page. The server is never globally locked for a read.</li>
 * </ul>
 *
 * <p><b>Deadlock-free by construction:</b> every method holds at most one page's lock at a time and
 * never nests page locks, so no lock-ordering cycle can form. The maps are {@link ConcurrentHashMap}s
 * with their own internal synchronization.
 */
public class WikiStore {

    private final ConcurrentHashMap<String, Page> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final long leaseMs;
    private final Clock clock;

    public WikiStore(long leaseMs, Clock clock) {
        this.leaseMs = leaseMs;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- validation
    private static void requireTitle(String t) throws ValidationException {
        if (t == null || t.trim().isEmpty()) throw new ValidationException("Tytuł strony nie może być pusty.");
        if (t.length() > 120) throw new ValidationException("Tytuł jest za długi (maks. 120 znaków).");
    }

    private static void requireContent(String c) throws ValidationException {
        if (c == null) throw new ValidationException("Treść strony nie może być pusta.");
        if (c.length() > 100_000) throw new ValidationException("Treść jest za długa (maks. 100000 znaków).");
    }

    // ---------------------------------------------------------------- users (accounts)
    /** Used by persistence/seed loading (no validation, no duplicate check). */
    public void addUser(User u) { users.put(u.username(), u); }

    public User getUser(String username) { return users.get(username); }

    public void createUser(String username, String salt, String hash, Role role) throws ValidationException {
        if (username == null || username.trim().isEmpty())
            throw new ValidationException("Nazwa użytkownika nie może być pusta.");
        if (users.putIfAbsent(username, new User(username, salt, hash, role)) != null)
            throw new ValidationException("Użytkownik '" + username + "' już istnieje.");
    }

    public void deleteUser(String username) throws NotFoundException {
        if (users.remove(username) == null)
            throw new NotFoundException("Nie znaleziono użytkownika: " + username);
    }

    /** Replace a user's salt+hash (role preserved). Used by changePassword. */
    public void updatePassword(String username, String salt, String hash) throws NotFoundException {
        User u = users.get(username);
        if (u == null) throw new NotFoundException("Nie znaleziono użytkownika: " + username);
        users.put(username, new User(username, salt, hash, u.role()));
    }

    public List<UserDTO> listUsers() {
        List<UserDTO> out = new ArrayList<>();
        for (User u : users.values()) out.add(new UserDTO(u.username(), u.role()));
        out.sort(Comparator.comparing(UserDTO::getUsername));
        return out;
    }

    /** Snapshot for persistence. */
    public Collection<User> allUsers() { return users.values(); }

    // ---------------------------------------------------------------- pages: create / delete
    public PageDTO createPage(String title, String content, String editor) throws ValidationException {
        requireTitle(title);
        String body = (content == null) ? "" : content;
        requireContent(body);
        long now = clock.now();
        Page p = new Page(title, body, 0, editor, now);

        // SEKCJA KRYTYCZNA — atomowe "utwórz, jeśli nie istnieje".
        // ROZWIĄZANIE UŻYTE: ConcurrentHashMap.putIfAbsent — pojedyncza operacja
        // ATOMOWA, bez jawnej blokady (lock-free, oparte na CAS w środku mapy).
        // Z N wątków tworzących ten sam tytuł dokładnie jeden dostaje prev == null.
        if (pages.putIfAbsent(title, p) != null)
            throw new ValidationException("Strona '" + title + "' już istnieje.");
        return toDTO(p, now);

        // WARIANT ALTERNATYWNY — zwykła HashMap + jawna synchronizacja:
        //   synchronized (pages) {                    // pages jako HashMap, nie ConcurrentHashMap
        //       if (pages.containsKey(title))
        //           throw new ValidationException("Strona '" + title + "' już istnieje.");
        //       pages.put(title, p);                  // "sprawdź-potem-wstaw" MUSI być w jednym bloku,
        //   }                                         // inaczej dwa wątki przejdą test i oba wstawią.
        //   Można też użyć Collections.synchronizedMap(new HashMap<>()), ale to czyni
        //   atomowymi tylko POJEDYNCZE metody — złożenie containsKey()+put() i tak
        //   wymaga zewnętrznego synchronized. ConcurrentHashMap.putIfAbsent jest
        //   prostsze i nie blokuje całej mapy.
    }

    public void deletePage(String title) throws NotFoundException {
        if (pages.remove(title) == null)
            throw new NotFoundException("Nie znaleziono strony: " + title);
    }

    /** Used by persistence load (rebuilds the page map). */
    public void putPage(Page p) { pages.put(p.title(), p); }

    /** Snapshot for persistence. */
    public Collection<Page> allPages() { return pages.values(); }

    private Page require(String title) throws NotFoundException {
        Page p = pages.get(title);
        if (p == null) throw new NotFoundException("Nie znaleziono strony: " + title);
        return p;
    }

    // ---------------------------------------------------------------- reads (shared read lock)
    public PageDTO getPage(String title) throws NotFoundException {
        Page p = require(title);
        // ODCZYT pod WSPÓŁDZIELONĄ blokadą odczytu (readLock): wielu czytelników
        // może czytać RÓWNOCZEŚNIE, ale żaden nie zobaczy strony w połowie zapisu,
        // bo zapis (savePage) bierze wyłączny writeLock. To kluczowa optymalizacja
        // dla aplikacji "dużo odczytów, mało zapisów" — serwer nie jest globalnie
        // blokowany na czas czytania strony przez innych użytkowników.
        p.lock().readLock().lock();
        try {
            return toDTO(p, clock.now());
        } finally {
            p.lock().readLock().unlock();
        }
        // WARIANT ALTERNATYWNY — synchronized (p): też poprawny (czytelnik nigdy
        // nie zobaczy częściowego zapisu), ale SERIALIZUJE odczyty — każdy czytelnik
        // czeka na poprzedniego. Przy wielu jednoczesnych odczytach ReadWriteLock
        // daje znacznie lepszą przepustowość.
    }

    public List<PageSummaryDTO> listPages() {
        List<PageSummaryDTO> out = new ArrayList<>();
        long now = clock.now();
        for (Page p : pages.values()) {
            p.lock().readLock().lock();
            try {
                out.add(toSummary(p, now));
            } finally {
                p.lock().readLock().unlock();
            }
        }
        out.sort(Comparator.comparing(PageSummaryDTO::getTitle));
        return out;
    }

    public List<PageSummaryDTO> search(String query) {
        String q = (query == null) ? "" : query.toLowerCase();
        long now = clock.now();
        List<PageSummaryDTO> out = new ArrayList<>();
        for (Page p : pages.values()) {
            p.lock().readLock().lock();
            try {
                if (q.isEmpty()
                        || p.title().toLowerCase().contains(q)
                        || p.content().toLowerCase().contains(q)) {
                    out.add(toSummary(p, now));
                }
            } finally {
                p.lock().readLock().unlock();
            }
        }
        out.sort(Comparator.comparing(PageSummaryDTO::getTitle));
        return out;
    }

    // ---------------------------------------------------------------- edit-lease (write lock)
    public LockInfoDTO acquireEditLock(String title, String token, String userName)
            throws NotFoundException, PageLockedException {
        Page p = require(title);
        long now = clock.now();

        // ====================================================================
        // SEKCJA KRYTYCZNA — "sprawdź i ustaw" (check-and-set) blokadę edycji.
        // To serce ochrony przed stanem wyścigu (race condition): gdy N klientów
        // jednocześnie wywoła tę metodę dla TEJ SAMEJ strony, blokada zapisu
        // dopuści do bloku tylko jeden wątek naraz, więc dokładnie jeden założy
        // dzierżawę, a pozostali zobaczą ją już ustawioną i dostaną wyjątek.
        //
        // ROZWIĄZANIE UŻYTE: ReentrantReadWriteLock.writeLock() z obiektu Page.
        //   + drobnoziarniste: blokada dotyczy JEDNEJ strony, nie całego serwera;
        //   + odczyty (getPage) biorą readLock i nie blokują się wzajemnie;
        //   + brak zakleszczeń: operacja trzyma najwyżej jedną blokadę strony.
        // ====================================================================
        p.lock().writeLock().lock();
        try {
            EditLock cur = p.editLock();
            if (cur != null && !cur.isExpired(now) && !cur.heldBy(token))
                throw new PageLockedException(cur.holderName(), Math.max(0, cur.expiresAt() - now) / 1000);
            EditLock l = new EditLock(token, userName, now, now + leaseMs);
            p.setEditLock(l);
            return toLockInfo(l, now);
        } finally {
            p.lock().writeLock().unlock();
        }

        // --------------------------------------------------------------------
        // TE SAME SEKCJE KRYTYCZNE — WARIANTY ALTERNATYWNE (zakomentowane).
        // Wszystkie poprawnie chronią sekcję krytyczną; różnią się właściwościami.
        //
        // WARIANT 1 — synchronized (monitor obiektu Page):
        //   synchronized (p) {
        //       EditLock cur = p.editLock();
        //       if (cur != null && !cur.isExpired(now) && !cur.heldBy(token))
        //           throw new PageLockedException(cur.holderName(),
        //                   Math.max(0, cur.expiresAt() - now) / 1000);
        //       EditLock l = new EditLock(token, userName, now, now + leaseMs);
        //       p.setEditLock(l);
        //       return toLockInfo(l, now);
        //   }
        //   Wada: monitor nie rozróżnia odczytu od zapisu — czytelnicy też by się
        //   serializowali, tracąc współbieżność odczytów (gorsze niż ReadWriteLock).
        //
        // WARIANT 2 — jawny ReentrantLock per-strona (wymaga pola w klasie Page,
        //             np. `private final ReentrantLock editMutex = new ReentrantLock();`):
        //   p.editMutex().lock();
        //   try { /* ta sama logika check-and-set */ }
        //   finally { p.editMutex().unlock(); }
        //   Zaleta: tryLock(timeout) i lockInterruptibly(); Wada: brak rozdziału R/W.
        //
        // WARIANT 3 — Semaphore(1) jako blokada binarna (pole w Page,
        //             np. `private final Semaphore editPermit = new Semaphore(1);`):
        //   p.editPermit().acquire();
        //   try { /* ta sama logika */ } finally { p.editPermit().release(); }
        //   Uwaga: semafor nie jest reentrantny i nie ma "właściciela" wątku —
        //   jako zwykła wzajemna wykluczność jest tu mniej naturalny niż lock.
        // --------------------------------------------------------------------
    }

    public LockInfoDTO renewEditLock(String title, String token)
            throws NotFoundException, PageLockedException {
        Page p = require(title);
        long now = clock.now();
        p.lock().writeLock().lock();
        try {
            EditLock cur = p.editLock();
            if (cur == null || cur.isExpired(now) || !cur.heldBy(token))
                throw new PageLockedException(cur == null ? "-" : cur.holderName(), 0);
            EditLock l = new EditLock(token, cur.holderName(), cur.acquiredAt(), now + leaseMs);
            p.setEditLock(l);
            return toLockInfo(l, now);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    public void releaseEditLock(String title, String token) throws NotFoundException {
        Page p = require(title);
        p.lock().writeLock().lock();
        try {
            EditLock cur = p.editLock();
            if (cur != null && cur.heldBy(token)) p.setEditLock(null);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    // ---------------------------------------------------------------- save (write lock)
    public PageDTO savePage(String title, String token, String userName, String newContent, long baseVersion)
            throws NotFoundException, AuthorizationException, VersionConflictException, ValidationException {
        requireContent(newContent);
        Page p = require(title);
        long now = clock.now();
        // SEKCJA KRYTYCZNA zapisu — pod wyłącznym writeLock. PODWÓJNA ochrona:
        //   1) blokada-dzierżawa: tylko posiadacz aktywnej dzierżawy może zapisać
        //      (pesymistycznie — inni nie weszli nawet w edycję);
        //   2) kontrola wersji (optymistycznie) — gdyby dzierżawa wygasła i ktoś
        //      zmienił stronę w międzyczasie, niezgodna baseVersion zatrzyma zapis.
        // Inkrementacja wersji i dopisanie rewizji są tu NIEPODZIELNE (atomowe),
        // więc nie zgubimy żadnej aktualizacji (brak "lost update").
        p.lock().writeLock().lock();
        try {
            EditLock cur = p.editLock();
            if (cur == null || cur.isExpired(now) || !cur.heldBy(token))
                throw new AuthorizationException("Brak aktywnej blokady tej strony — nie można zapisać.");
            if (p.version() != baseVersion)
                throw new VersionConflictException(p.version());
            long newVersion = p.version() + 1;
            p.setContent(newContent);
            p.setVersion(newVersion);
            p.setLastEditor(userName);
            p.setLastModified(now);
            p.history().add(new RevisionDTO((int) newVersion, userName, now, newContent));
            p.setEditLock(null);                            // editing finished -> release lease
            return toDTO(p, now);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    // ---------------------------------------------------------------- history (read lock)
    public List<RevisionDTO> getHistory(String title) throws NotFoundException {
        Page p = require(title);
        p.lock().readLock().lock();
        try {
            return new ArrayList<>(p.history());
        } finally {
            p.lock().readLock().unlock();
        }
    }

    public PageDTO getRevision(String title, int revisionIndex) throws NotFoundException, ValidationException {
        Page p = require(title);
        p.lock().readLock().lock();
        try {
            if (revisionIndex < 1 || revisionIndex > p.history().size())
                throw new ValidationException("Brak rewizji o numerze " + revisionIndex + ".");
            RevisionDTO r = p.history().get(revisionIndex - 1);
            return new PageDTO(p.title(), r.getContent(), r.getIndex(), r.getEditor(), r.getTimestamp(), null);
        } finally {
            p.lock().readLock().unlock();
        }
    }

    /** Restore an old revision's content as a NEW revision (preserves history). */
    public PageDTO restoreRevision(String title, String token, String userName, int revisionIndex)
            throws NotFoundException, ValidationException, PageLockedException {
        Page p = require(title);
        long now = clock.now();
        p.lock().writeLock().lock();
        try {
            EditLock cur = p.editLock();
            if (cur != null && !cur.isExpired(now) && !cur.heldBy(token))
                throw new PageLockedException(cur.holderName(), Math.max(0, cur.expiresAt() - now) / 1000);
            if (revisionIndex < 1 || revisionIndex > p.history().size())
                throw new ValidationException("Brak rewizji o numerze " + revisionIndex + ".");
            RevisionDTO old = p.history().get(revisionIndex - 1);
            long newVersion = p.version() + 1;
            p.setContent(old.getContent());
            p.setVersion(newVersion);
            p.setLastEditor(userName);
            p.setLastModified(now);
            p.history().add(new RevisionDTO((int) newVersion,
                    userName + " (przywrócono z v" + revisionIndex + ")", now, old.getContent()));
            p.setEditLock(null);
            return toDTO(p, now);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    // ---------------------------------------------------------------- reaper hook (write lock)
    /** Force-clear any edit-lock on a page regardless of holder (admin override). */
    public void forceUnlock(String title) throws NotFoundException {
        Page p = require(title);
        p.lock().writeLock().lock();
        try {
            p.setEditLock(null);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    /** Reclaim every expired edit-lease; returns the titles freed. Called by the reaper daemon. */
    public List<String> reapExpiredLocks() {
        long now = clock.now();
        List<String> freed = new ArrayList<>();
        for (Page p : pages.values()) {
            p.lock().writeLock().lock();
            try {
                EditLock l = p.editLock();
                if (l != null && l.isExpired(now)) {
                    p.setEditLock(null);
                    freed.add(p.title());
                }
            } finally {
                p.lock().writeLock().unlock();
            }
        }
        return freed;
    }

    // ---------------------------------------------------------------- mappers (caller holds the lock)
    private PageDTO toDTO(Page p, long now) {
        return new PageDTO(p.title(), p.content(), p.version(), p.lastEditor(), p.lastModified(),
                toLockInfo(p.editLock(), now));
    }

    private PageSummaryDTO toSummary(Page p, long now) {
        EditLock l = p.editLock();
        String by = (l != null && !l.isExpired(now)) ? l.holderName() : null;
        return new PageSummaryDTO(p.title(), p.version(), by, p.lastModified());
    }

    private LockInfoDTO toLockInfo(EditLock l, long now) {
        if (l == null || l.isExpired(now)) return null;
        return new LockInfoDTO(l.holderName(), l.acquiredAt(), l.expiresAt(), Math.max(0, l.expiresAt() - now));
    }
}
