package wikirmi.test;

import wikirmi.common.Role;
import wikirmi.common.exceptions.WikiException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/** Test SEMAFORA i sesji w WikiStore: limit klientów, logowanie/wylogowanie, role. */
public class SemaforTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM, 2);          // limit 2 klientów (SEMAFOR)
        String tAdmin = s.openSession("alice", Role.ADMIN);          // 1. pozwolenie
        String tUser = s.openSession("bob", Role.USER);              // 2. pozwolenie — semafor pełny

        Assert.eq(s.requireSession(tAdmin).username, "alice", "rozpoznanie sesji po tokenie");
        Assert.eq(s.requireSession(tUser).role, Role.USER, "rola z sesji");

        java.util.List<String> online = s.onlineUsernames();
        Assert.isTrue(online.contains("alice") && online.contains("bob"), "lista użytkowników online");
        Assert.eq(online.size(), 2, "dwóch różnych użytkowników online");

        // kontrola ról (gdy obie sesje są ważne)
        Assert.isTrue(s.requireSession(tUser) != null, "USER przechodzi podstawową kontrolę (może tworzyć strony)");
        Assert.throwsEx(WikiException.class, () -> s.requireAdmin(tUser),
                "USER nie przechodzi kontroli administratora (nie usunie strony)");
        Assert.isTrue(s.requireAdmin(tAdmin) != null, "ADMIN przechodzi kontrolę administratora");

        // SEMAFOR: trzecie logowanie ponad limit jest odrzucane
        Assert.throwsEx(WikiException.class, () -> s.openSession("carol", Role.USER),
                "trzecie logowanie ponad limit semafora jest odrzucane");

        // po wylogowaniu jednego klienta pozwolenie wraca i nowy może się zalogować
        s.closeSession(tUser);
        String tCarol = s.openSession("carol", Role.USER);
        Assert.eq(s.requireSession(tCarol).username, "carol", "pozwolenie odzyskane po wylogowaniu");

        Assert.throwsEx(WikiException.class, () -> s.requireSession("zly-token"),
                "nieznany token jest odrzucany");
    }
}
