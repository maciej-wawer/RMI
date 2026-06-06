package wikirmi.server.auth;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import wikirmi.common.Role;
import wikirmi.common.exceptions.AuthenticationException;
import wikirmi.common.exceptions.AuthorizationException;

/**
 * Tracks logged-in sessions (token -> session) and enforces the maximum number of
 * concurrent clients with a counting {@link Semaphore}. Thread-safe.
 */
public class SessionManager {

    public static final class Session {
        public final String token;
        public final String username;
        public final Role role;
        public volatile long lastSeen;
        Session(String token, String username, Role role, long now) {
            this.token = token; this.username = username; this.role = role; this.lastSeen = now;
        }
    }

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Semaphore permits;

    public SessionManager(int maxClients) {
        this.permits = new Semaphore(maxClients);
    }

    /** Opens a session, consuming one client permit; rejects when the server is full. */
    public String open(String username, Role role) throws AuthenticationException {
        // LIMIT JEDNOCZESNYCH KLIENTÓW — licznik zasobów.
        // ROZWIĄZANIE UŻYTE: Semaphore z MAX_CLIENTS pozwoleniami. tryAcquire()
        // pobiera pozwolenie bez blokowania; gdy brak pozwoleń => odrzucamy login.
        // close() zwraca pozwolenie (release). Semafor sam dba o atomowość licznika.
        if (!permits.tryAcquire())
            throw new AuthenticationException("Serwer jest pełny — przekroczono maksymalną liczbę klientów.");
        String token = UUID.randomUUID().toString();
        sessions.put(token, new Session(token, username, role, System.currentTimeMillis()));
        return token;

        // WARIANT ALTERNATYWNY — AtomicInteger z pętlą CAS (compare-and-set):
        //   int n;
        //   do {
        //       n = activeCount.get();                 // activeCount: AtomicInteger
        //       if (n >= maxClients)
        //           throw new AuthenticationException("Serwer jest pełny ...");
        //   } while (!activeCount.compareAndSet(n, n + 1));   // atomowa inkrementacja
        //   ... a przy close(): activeCount.decrementAndGet();
        //   Działa bez blokad, ale Semaphore jest czytelniejszy i dodatkowo wspiera
        //   blokujące acquire() oraz acquire(timeout), gdybyśmy chcieli KOLEJKOWAĆ
        //   klientów zamiast ich odrzucać.
        //
        // WARIANT ALTERNATYWNY 2 — synchronized na liczniku:
        //   synchronized (this) { if (count >= max) throw ...; count++; }
        //   Proste, ale serializuje każde logowanie na monitorze SessionManagera.
    }

    /** Resolves a token to its session, refreshing lastSeen; throws if unknown/expired. */
    public Session require(String token) throws AuthenticationException {
        Session s = (token == null) ? null : sessions.get(token);
        if (s == null)
            throw new AuthenticationException("Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.");
        s.lastSeen = System.currentTimeMillis();
        return s;
    }

    /** Like {@link #require} but additionally requires the ADMIN role. */
    public Session requireAdmin(String token) throws AuthenticationException, AuthorizationException {
        Session s = require(token);
        if (s.role != Role.ADMIN)
            throw new AuthorizationException("Operacja dozwolona tylko dla administratora.");
        return s;
    }

    /** Closes a session and releases its client permit. */
    public void close(String token) {
        if (token != null && sessions.remove(token) != null) permits.release();
    }

    public int activeCount() { return sessions.size(); }

    /** Distinct usernames of currently logged-in sessions, sorted. */
    public java.util.List<String> onlineUsernames() {
        java.util.TreeSet<String> distinct = new java.util.TreeSet<>();
        for (Session s : sessions.values()) distinct.add(s.username);
        return new java.util.ArrayList<>(distinct);
    }
}
