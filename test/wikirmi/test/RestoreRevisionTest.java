package wikirmi.test;

import wikirmi.common.dto.PageDTO;
import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/** Restoring an old revision writes its content as a NEW revision and bumps the version. */
public class RestoreRevisionTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM);
        s.createPage("Doc", "v0", "admin");
        s.acquireEditLock("Doc", "t", "admin"); s.savePage("Doc", "t", "admin", "ALPHA", 0); // -> v1
        s.acquireEditLock("Doc", "t", "admin"); s.savePage("Doc", "t", "admin", "BETA", 1);  // -> v2

        PageDTO r = s.restoreRevision("Doc", "t", "admin", 1);                                // restore v1 (ALPHA)
        Assert.eq(r.getVersion(), 3, "restore creates a new version (v3)");
        Assert.eq(r.getContent(), "ALPHA", "restored content equals revision 1");
        Assert.eq(s.getHistory("Doc").size(), 3, "history preserved and grew to 3 revisions");

        // restore must be blocked while another user holds the lock
        s.acquireEditLock("Doc", "other", "bob");
        Assert.throwsEx(PageLockedException.class,
                () -> s.restoreRevision("Doc", "t", "admin", 1), "restore blocked while locked by another user");
    }
}
