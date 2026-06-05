package wikirmi.test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/** N threads create the SAME page title simultaneously: exactly one must succeed. */
public class CreatePageRaceTest {
    public static void run() throws Exception {
        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        int n = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        ExecutorService es = Executors.newFixedThreadPool(n);
        for (int i = 0; i < n; i++) {
            es.submit(() -> {
                try {
                    start.await();
                    store.createPage("Same", "x", "admin");
                    ok.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        es.shutdownNow();
        Assert.eq(ok.get(), 1, "exactly one createPage succeeds");
        Assert.eq(fail.get(), n - 1, "all other creators are rejected");
    }
}
