package wikirmi.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import wikirmi.common.dto.*;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Concurrent readers never observe a half-written page. A writer repeatedly saves content of
 * the form "len=V|V"; three readers continuously verify the two halves always match. With the
 * per-page read/write lock there must never be a torn read, and the writer must still make
 * progress concurrently.
 */
public class ReadersWriterTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM);
        s.createPage("RW", "len=0|0", "admin");
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<String> torn = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            long v = 0;
            try {
                while (!stop.get()) {
                    String tok = "w";
                    s.acquireEditLock("RW", tok, "w");
                    v++;
                    s.savePage("RW", tok, "w", "len=" + v + "|" + v, v - 1);
                }
            } catch (Exception ignored) { }
        });

        Runnable reader = () -> {
            try {
                while (!stop.get()) {
                    Dto.Page p = s.getPage("RW");
                    String[] kv = p.getContent().substring(4).split("\\|");
                    if (!kv[0].equals(kv[1])) { torn.set("torn read: " + p.getContent()); return; }
                }
            } catch (Exception ignored) { }
        };
        Thread r1 = new Thread(reader), r2 = new Thread(reader), r3 = new Thread(reader);

        writer.start(); r1.start(); r2.start(); r3.start();
        Thread.sleep(500);
        stop.set(true);
        writer.join(); r1.join(); r2.join(); r3.join();

        Assert.isTrue(torn.get() == null, "readers never observed a torn write (" + torn.get() + ")");
        Assert.isTrue(s.getPage("RW").getVersion() > 0, "writer made progress concurrently with readers");
    }
}
