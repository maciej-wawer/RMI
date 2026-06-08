package wikirmi.test;

import wikirmi.common.dto.*;
import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Proves the reaper logic deterministically with a manual clock: a lock held past its lease is
 * reclaimed by {@code reapExpiredLocks()} (what the daemon calls each tick), after which another
 * user can acquire it. No real waiting involved.
 */
public class LockExpiryTest {
    public static void run() throws Exception {
        Clock.Manual clock = new Clock.Manual(0);
        WikiStore s = new WikiStore(30000, clock);                // 30s lease
        s.createPage("P", "x", "admin");

        s.acquireEditLock("P", "alice", "alice");
        Assert.throwsEx(PageLockedException.class,
                () -> s.acquireEditLock("P", "bob", "bob"), "page is locked before expiry");

        clock.advance(31000);                                     // lease has now elapsed
        java.util.List<String> freed = s.reapExpiredLocks();      // one reaper tick
        Assert.isTrue(freed.contains("P"), "reaper reclaimed the expired lock");

        Dto.LockInfo l = s.acquireEditLock("P", "bob", "bob");     // bob can now edit
        Assert.isTrue(l != null, "another user acquires the lock after reaping");
    }
}
