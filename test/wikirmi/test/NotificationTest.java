package wikirmi.test;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicInteger;

import wikirmi.common.WikiClientCallback;
import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageSummaryDTO;
import wikirmi.server.notify.NotificationService;

/** A live client receives pushes; a client whose callback throws RemoteException is dropped. */
public class NotificationTest {

    static final class FakeClient implements WikiClientCallback {
        final AtomicInteger changed = new AtomicInteger();
        final boolean dead;
        FakeClient(boolean dead) { this.dead = dead; }
        public void onPageCreated(PageSummaryDTO p) {}
        public void onPageChanged(PageSummaryDTO p) throws RemoteException {
            if (dead) throw new RemoteException("client disconnected");
            changed.incrementAndGet();
        }
        public void onPageDeleted(String t) {}
        public void onLockChanged(String t, LockInfoDTO l) {}
        public void onPresenceChanged() {}
    }

    public static void run() throws Exception {
        NotificationService ns = new NotificationService();
        FakeClient good = new FakeClient(false);
        FakeClient dead = new FakeClient(true);
        ns.subscribe("good", good);
        ns.subscribe("dead", dead);

        ns.pageChanged(new PageSummaryDTO("X", 1, null, 0));
        Thread.sleep(300);                                        // let the dispatch executor run

        Assert.eq(good.changed.get(), 1, "live client received the push");
        Assert.eq(ns.subscriberCount(), 1, "dead client (RemoteException) was dropped");
        ns.shutdown();
    }
}
