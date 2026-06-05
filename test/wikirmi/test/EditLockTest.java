package wikirmi.test;

import wikirmi.server.model.EditLock;

/** Unit test for the edit-lease value object. */
public class EditLockTest {
    public static void run() {
        EditLock l = new EditLock("tokA", "alice", 1000, 1000 + 30000);
        Assert.isTrue(l.heldBy("tokA"), "held by its owner");
        Assert.isTrue(!l.heldBy("tokB"), "not held by another token");
        Assert.isTrue(!l.isExpired(1000 + 29999), "not expired one ms before deadline");
        Assert.isTrue(l.isExpired(1000 + 30000), "expired at the deadline");
        Assert.isTrue(l.isExpired(1000 + 40000), "expired after the deadline");
    }
}
