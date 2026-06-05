package wikirmi.test;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import wikirmi.client.ClientCallbackImpl;
import wikirmi.client.WikiEvents;
import wikirmi.client.service.WikiClientController;
import wikirmi.common.Role;
import wikirmi.common.dto.*;
import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.WikiServiceImpl;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.auth.SessionManager;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Standalone end-to-end test over REAL RMI (two client controllers + a live server). Proves the
 * edit-lock race is prevented across the network and that server-&gt;client callbacks deliver.
 * Run with: java -cp out wikirmi.test.RmiIntegrationTest
 */
public class RmiIntegrationTest {
    public static void main(String[] args) throws Exception {
        int port = 1101;

        // ---- server ----
        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        String salt = PasswordHasher.newSalt();
        store.addUser(new User("admin", salt, PasswordHasher.hash("admin123", salt), Role.ADMIN));
        SessionManager sessions = new SessionManager(10);
        NotificationService notify = new NotificationService();
        WikiServiceImpl impl = new WikiServiceImpl(store, sessions, notify, () -> { });
        Registry reg = LocateRegistry.createRegistry(port);
        reg.rebind("WikiService", impl);

        // ---- client A (admin): creates a page, will edit it ----
        WikiClientController a = new WikiClientController();
        a.connect("localhost", port);
        a.login("admin", "admin123");
        a.createPage("Wspólna", "wersja 0");

        // ---- client B: subscribes with a recording callback ----
        WikiClientController b = new WikiClientController();
        b.connect("localhost", port);
        b.login("admin", "admin123");
        AtomicInteger changed = new AtomicInteger();
        AtomicReference<String> lastLockHolder = new AtomicReference<>("(none)");
        WikiEvents recorder = new WikiEvents() {
            public void pageCreated(PageSummaryDTO p) { }
            public void pageChanged(PageSummaryDTO p) { changed.incrementAndGet(); }
            public void pageDeleted(String t) { }
            public void lockChanged(String t, LockInfoDTO l) { lastLockHolder.set(l == null ? null : l.getHolder()); }
        };
        ClientCallbackImpl cb = new ClientCallbackImpl(recorder);
        b.subscribe(cb);

        // ---- A acquires the edit-lock; B must be rejected (race prevented over RMI) ----
        a.acquireEditLock("Wspólna");
        boolean bRejected = false;
        try { b.acquireEditLock("Wspólna"); } catch (PageLockedException ex) { bRejected = true; }
        check(bRejected, "B rejected with PageLockedException while A holds the lock (over RMI)");

        // ---- A saves; version bumps; B's callback should fire ----
        PageDTO saved = a.savePage("Wspólna", "wersja 1 (A)", 0);
        check(saved.getVersion() == 1, "save over RMI bumped version to 1");
        Thread.sleep(400);
        check(changed.get() >= 1, "client B received onPageChanged callback (count=" + changed.get() + ")");

        // ---- now B can acquire the freed lock ----
        LockInfoDTO bl = b.acquireEditLock("Wspólna");
        check(bl != null, "B acquires the lock after A finished");

        System.out.println("RMI INTEGRATION OK: race prevented over the wire, save persisted, callback delivered.");
        notify.shutdown();
        System.exit(0);
    }

    static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("FAIL: " + msg);
        System.out.println("  ok: " + msg);
    }
}
