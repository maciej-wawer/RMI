package wikirmi.test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * THE headline race-condition proof: 10 client threads try to acquire the edit-lock on the
 * SAME page at the same instant. The system must let exactly ONE through and reject the
 * other nine with {@link PageLockedException} — i.e. the race is prevented.
 */
public class ConcurrencyTest {
    public static void run() throws Exception {
        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        store.createPage("Race", "seed", "admin");

        int n = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger acquired = new AtomicInteger(), rejected = new AtomicInteger();
        ExecutorService es = Executors.newFixedThreadPool(n);

        for (int i = 0; i < n; i++) {
            final String token = "tok" + i, name = "user" + i;
            es.submit(() -> {
                try {
                    start.await();                              // all threads fire together
                    store.acquireEditLock("Race", token, name);
                    acquired.incrementAndGet();
                } catch (PageLockedException locked) {
                    rejected.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        es.shutdownNow();

        System.out.println("    [ConcurrencyTest] acquired=" + acquired.get()
                + "  rejected(locked)=" + rejected.get() + "  of " + n + " threads");
        Assert.eq(acquired.get(), 1, "exactly ONE client acquires the edit-lock");
        Assert.eq(rejected.get(), n - 1, "all others get PageLockedException (race condition prevented)");
    }
}
