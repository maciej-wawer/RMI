package wikirmi.server.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import wikirmi.common.Role;
import wikirmi.common.dto.*;
import wikirmi.common.exceptions.*;
import wikirmi.server.model.*;

/**
 * ===========================================================================
 *  RDZEŃ WSPÓŁBIEŻNOŚCI SERWERA  —  cały projekt w jednym miejscu.
 *
 *  Ta klasa celowo pokazuje WSZYSTKIE CZTERY mechanizmy programowania
 *  współbieżnego, każdy w osobnej, wyraźnie oznaczonej sekcji:
 *
 *    1) BLOKADY   (ReentrantReadWriteLock) — edycja stron: wielu czytelników
 *                 naraz, ale tylko JEDEN piszący; chroni przed stanem wyścigu.
 *    2) MONITORY  (synchronized)           — wspólny licznik statystyk; klasyczny
 *                 monitor chroniący zmienną dzieloną przez wiele wątków.
 *    3) SEMAFORY  (Semaphore)              — limit jednoczesnych klientów.
 *    4) WĄTKI     (Thread, daemon)         — wątek w tle czyszczący stare blokady.
 *
 *  Dlaczego to potrzebne? Serwer RMI obsługuje każde wywołanie klienta w OSOBNYM
 *  wątku. Gdy dwóch klientów jednocześnie sięga po ten sam zasób (np. edytuje tę
 *  samą stronę), bez synchronizacji wystąpiłby "stan wyścigu" (race condition) —
 *  np. jeden zapis nadpisałby drugi. Poniższe mechanizmy temu zapobiegają.
 *
 *  Brak zakleszczeń (deadlock): każda metoda trzyma NAJWYŻEJ JEDNĄ blokadę strony
 *  naraz i nie zagnieżdża blokad — więc nie może powstać cykl oczekiwań.
 * ===========================================================================
 */
public class WikiStore {

    // mapy bezpieczne wątkowo (ConcurrentHashMap same w sobie pilnują spójności)
    private final ConcurrentHashMap<String, Page> pages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    private final long leaseMs;          // jak długo ważna jest blokada edycji
    private final Clock clock;           // źródło czasu (w testach podmieniane)

    // --- pola mechanizmów współbieżności ---
    private final Semaphore wolneMiejsca;        // SEMAFOR: limit klientów
    private Thread watekSprzatajacy;             // WĄTEK daemon: czyszczenie blokad
    private final Object monitorStatystyk = new Object();   // MONITOR
    private int licznikZapisow = 0;              // chroniony przez monitorStatystyk

    public WikiStore(long leaseMs, Clock clock) {
        this(leaseMs, clock, 50);
    }

    public WikiStore(long leaseMs, Clock clock, int maksKlientow) {
        this.leaseMs = leaseMs;
        this.clock = clock;
        this.wolneMiejsca = new Semaphore(maksKlientow);   // tyle pozwoleń = tylu klientów naraz
    }

    // =======================================================================
    //  MECHANIZM 3: SEMAFORY (Semaphore) — limit jednoczesnych klientów + sesje
    // =======================================================================
    // Semafor to "licznik pozwoleń". Każde zalogowanie pobiera jedno pozwolenie
    // (tryAcquire), wylogowanie je oddaje (release). Gdy pozwoleń brak — odmawiamy
    // logowania. To prosty sposób na ograniczenie liczby jednoczesnych klientów.

    /** Sesja zalogowanego użytkownika (token + kto + rola). */
    public static final class Session {
        public final String token;
        public final String username;
        public final Role role;
        public volatile long lastSeen;          // volatile = bezpieczna widoczność między wątkami
        Session(String token, String username, Role role, long now) {
            this.token = token; this.username = username; this.role = role; this.lastSeen = now;
        }
    }

    /** Logowanie: pobiera pozwolenie z semafora; gdy brak — serwer pełny. */
    public String openSession(String username, Role role) throws AuthenticationException {
        if (!wolneMiejsca.tryAcquire())
            throw new AuthenticationException("Serwer jest pełny — przekroczono maksymalną liczbę klientów.");
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(token, username, role, System.currentTimeMillis()));
        return token;
    }

    /** Sprawdza token i zwraca sesję; rzuca wyjątek, gdy sesja nieznana/wygasła. */
    public Session requireSession(String token) throws AuthenticationException {
        Session s = (token == null) ? null : sessions.get(token);
        if (s == null)
            throw new AuthenticationException("Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.");
        s.lastSeen = System.currentTimeMillis();
        return s;
    }

    /** Jak requireSession, ale dodatkowo wymaga roli administratora. */
    public Session requireAdmin(String token) throws AuthenticationException, AuthorizationException {
        Session s = requireSession(token);
        if (s.role != Role.ADMIN)
            throw new AuthorizationException("Operacja dozwolona tylko dla administratora.");
        return s;
    }

    /** Wylogowanie: usuwa sesję i ODDAJE pozwolenie do semafora. */
    public void closeSession(String token) {
        if (token != null && sessions.remove(token) != null)
            wolneMiejsca.release();
    }

    /** Lista (różnych) nazw zalogowanych użytkowników. */
    public List<String> onlineUsernames() {
        java.util.TreeSet<String> rozne = new java.util.TreeSet<>();
        for (Session s : sessions.values()) rozne.add(s.username);
        return new ArrayList<>(rozne);
    }

    // =======================================================================
    //  MECHANIZM 2: MONITORY (synchronized) — wspólny licznik statystyk
    // =======================================================================
    // Monitor to najprostsza forma wzajemnego wykluczania w Javie: blok
    // "synchronized (obiekt) { ... }" wpuszcza tylko jeden wątek naraz na danym
    // obiekcie. Tu chronimy zwykłą zmienną int dzieloną przez wiele wątków zapisu —
    // bez synchronized "licznik++" gubiłby zliczenia (odczyt-dodaj-zapis nie jest
    // atomowe). Z monitorem inkrementacja jest niepodzielna.

    private void zliczZapis() {
        synchronized (monitorStatystyk) {
            licznikZapisow++;
        }
    }

    /** Ile zapisów wykonano od startu serwera (czytane spod monitora). */
    public int licznikZapisow() {
        synchronized (monitorStatystyk) {
            return licznikZapisow;
        }
    }

    // =======================================================================
    //  MECHANIZM 4: WĄTKI (Thread, daemon) — sprzątanie starych blokad w tle
    // =======================================================================
    // Osobny wątek działający w pętli co kilka sekund zwalnia przeterminowane
    // blokady edycji (np. gdy klient zamknął edytor albo się zawiesił). Jako
    // wątek "daemon" nie blokuje zamknięcia programu.

    public void startReaper(long okresMs, Consumer<String> poZwolnieniu) {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(okresMs);
                } catch (InterruptedException stop) {
                    return;                              // przerwano = kończymy wątek
                }
                for (String tytul : reapExpiredLocks())  // znajdź i zwolnij wygasłe blokady
                    if (poZwolnieniu != null) poZwolnieniu.accept(tytul);
            }
        }, "watek-sprzatajacy");
        t.setDaemon(true);                               // wątek tła — nie wstrzymuje zamknięcia JVM
        t.start();
        this.watekSprzatajacy = t;
    }

    public void stopReaper() {
        Thread t = watekSprzatajacy;
        if (t != null) t.interrupt();
    }

    /** Przechodzi po stronach i zwalnia te blokady, które już wygasły. */
    public List<String> reapExpiredLocks() {
        long now = clock.now();
        List<String> zwolnione = new ArrayList<>();
        for (Page p : pages.values()) {
            p.lock().writeLock().lock();                 // BLOKADA zapisu na czas zmiany pola
            try {
                EditLock l = p.editLock();
                if (l != null && l.isExpired(now)) {
                    p.setEditLock(null);
                    zwolnione.add(p.title());
                }
            } finally {
                p.lock().writeLock().unlock();
            }
        }
        return zwolnione;
    }

    // ---------------------------------------------------------------- walidacja danych wejściowych
    private static void requireTitle(String t) throws ValidationException {
        if (t == null || t.trim().isEmpty()) throw new ValidationException("Tytuł strony nie może być pusty.");
        if (t.length() > 120) throw new ValidationException("Tytuł jest za długi (maks. 120 znaków).");
    }

    private static void requireContent(String c) throws ValidationException {
        if (c == null) throw new ValidationException("Treść strony nie może być pusta.");
        if (c.length() > 100_000) throw new ValidationException("Treść jest za długa (maks. 100000 znaków).");
    }

    // ---------------------------------------------------------------- konta użytkowników
    public void addUser(User u) { users.put(u.username(), u); }       // używane przy wczytywaniu z pliku
    public User getUser(String username) { return users.get(username); }

    public void createUser(String username, String salt, String hash, Role role) throws ValidationException {
        if (username == null || username.trim().isEmpty())
            throw new ValidationException("Nazwa użytkownika nie może być pusta.");
        // putIfAbsent = atomowe "dodaj, jeśli nie istnieje" (jeden wątek wygrywa)
        if (users.putIfAbsent(username, new User(username, salt, hash, role)) != null)
            throw new ValidationException("Użytkownik '" + username + "' już istnieje.");
    }

    public void deleteUser(String username) throws NotFoundException {
        if (users.remove(username) == null)
            throw new NotFoundException("Nie znaleziono użytkownika: " + username);
    }

    /** Podmiana hasła (sól + skrót); rola bez zmian. */
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

    public Collection<User> allUsers() { return users.values(); }     // migawka do zapisu

    // ---------------------------------------------------------------- strony: tworzenie / usuwanie
    public PageDTO createPage(String title, String content, String editor) throws ValidationException {
        requireTitle(title);
        String body = (content == null) ? "" : content;
        requireContent(body);
        long now = clock.now();
        Page p = new Page(title, body, 0, editor, now);
        // atomowe "utwórz, jeśli nie istnieje": z N wątków tworzących ten sam tytuł
        // dokładnie jeden dostaje prev == null i wygrywa — reszta dostaje wyjątek.
        if (pages.putIfAbsent(title, p) != null)
            throw new ValidationException("Strona '" + title + "' już istnieje.");
        return toDTO(p, now);
    }

    public void deletePage(String title) throws NotFoundException {
        if (pages.remove(title) == null)
            throw new NotFoundException("Nie znaleziono strony: " + title);
    }

    public void putPage(Page p) { pages.put(p.title(), p); }          // używane przy wczytywaniu z pliku
    public Collection<Page> allPages() { return pages.values(); }     // migawka do zapisu

    private Page require(String title) throws NotFoundException {
        Page p = pages.get(title);
        if (p == null) throw new NotFoundException("Nie znaleziono strony: " + title);
        return p;
    }

    // =======================================================================
    //  MECHANIZM 1: BLOKADY (ReentrantReadWriteLock) — odczyt/edycja stron
    // =======================================================================
    // Każda strona ma WŁASNĄ blokadę odczytu/zapisu (blokowanie drobnoziarniste —
    // blokujemy pojedynczą stronę, a nie cały serwer). readLock() wpuszcza wielu
    // czytelników naraz; writeLock() jest wyłączny (jeden piszący, zero czytelników).

    /** Odczyt strony pod WSPÓŁDZIELONĄ blokadą odczytu (wielu czytelników naraz). */
    public PageDTO getPage(String title) throws NotFoundException {
        Page p = require(title);
        p.lock().readLock().lock();
        try {
            return toDTO(p, clock.now());
        } finally {
            p.lock().readLock().unlock();
        }
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
                if (q.isEmpty() || p.title().toLowerCase().contains(q) || p.content().toLowerCase().contains(q))
                    out.add(toSummary(p, now));
            } finally {
                p.lock().readLock().unlock();
            }
        }
        out.sort(Comparator.comparing(PageSummaryDTO::getTitle));
        return out;
    }

    /**
     * SEKCJA KRYTYCZNA — "sprawdź i ustaw" blokadę edycji (ochrona przed wyścigiem).
     * Gdy N klientów jednocześnie wywoła tę metodę dla tej samej strony, writeLock
     * wpuści tylko jeden wątek naraz, więc DOKŁADNIE JEDEN założy dzierżawę edycji,
     * a pozostali zobaczą ją zajętą i dostaną PageLockedException.
     */
    public LockInfoDTO acquireEditLock(String title, String token, String userName)
            throws NotFoundException, PageLockedException {
        Page p = require(title);
        long now = clock.now();
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
        // Wariant alternatywny tej samej sekcji krytycznej: zamiast writeLock można
        // użyć "synchronized (p) { ... }" (monitor) albo ReentrantLock — działają tak
        // samo, ale nie rozdzielają odczytu od zapisu (czytelnicy też by się blokowali).
    }

    /** Odnowienie dzierżawy (klient co kilka sekund przedłuża swoją blokadę). */
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

    /** Zwolnienie blokady (anulowanie edycji). */
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

    /**
     * Zapis strony pod WYŁĄCZNYM writeLock. PODWÓJNA ochrona:
     *  1) tylko posiadacz aktywnej dzierżawy może zapisać (pesymistycznie),
     *  2) zgodność numeru wersji (optymistycznie) — gdyby dzierżawa wygasła i ktoś
     *     zmienił stronę, niezgodna baseVersion zatrzyma zapis.
     * Inkrementacja wersji + dopis do historii są niepodzielne → brak "lost update".
     */
    public PageDTO savePage(String title, String token, String userName, String newContent, long baseVersion)
            throws NotFoundException, AuthorizationException, VersionConflictException, ValidationException {
        requireContent(newContent);
        Page p = require(title);
        long now = clock.now();
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
            p.setEditLock(null);                            // koniec edycji -> zwolnij dzierżawę
            zliczZapis();                                   // MONITOR: policz zapis (synchronized)
            return toDTO(p, now);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    // ---------------------------------------------------------------- historia (blokada odczytu)
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

    /** Przywraca starą rewizję jako NOWĄ wersję (zachowuje pełną historię). */
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

    /** Wymuszone zdjęcie blokady (administrator), niezależnie od posiadacza. */
    public void forceUnlock(String title) throws NotFoundException {
        Page p = require(title);
        p.lock().writeLock().lock();
        try {
            p.setEditLock(null);
        } finally {
            p.lock().writeLock().unlock();
        }
    }

    // ---------------------------------------------------------------- mapowanie na DTO (pod blokadą wołającego)
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
