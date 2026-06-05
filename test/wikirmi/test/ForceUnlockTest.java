package wikirmi.test;

import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/** Admin force-unlock clears any held lock so another user can immediately acquire it. */
public class ForceUnlockTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM);
        s.createPage("P", "x", "admin");
        s.acquireEditLock("P", "alice", "alice");
        Assert.throwsEx(PageLockedException.class,
                () -> s.acquireEditLock("P", "bob", "bob"), "locked by alice before force-unlock");

        s.forceUnlock("P");

        LockInfoDTO l = s.acquireEditLock("P", "bob", "bob");
        Assert.isTrue(l != null, "bob acquires the lock right after force-unlock");
    }
}
