package wikirmi.server.daemon;

import java.util.List;

import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.WikiStore;

/**
 * Background daemon that periodically reclaims expired edit-leases — e.g. when a client crashed
 * or closed its editor without releasing the lock. Runs as a daemon thread so it never blocks
 * JVM shutdown. This is the "stale reservation cleanup" the assignment asks for.
 */
public class LockReaperDaemon {
    private final WikiStore store;
    private final NotificationService notify;     // may be null (e.g. in tests)
    private final long periodMs;
    private volatile Thread thread;

    public LockReaperDaemon(WikiStore store, NotificationService notify, long periodMs) {
        this.store = store;
        this.notify = notify;
        this.periodMs = periodMs;
    }

    public void start() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(periodMs);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                    return;
                }
                List<String> freed = store.reapExpiredLocks();
                if (notify != null) {
                    for (String title : freed) notify.lockChanged(title, null);
                }
            }
        }, "lock-reaper");
        t.setDaemon(true);
        t.start();
        this.thread = t;
    }

    public void stop() {
        Thread t = thread;
        if (t != null) t.interrupt();
    }
}
