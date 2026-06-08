package wikirmi.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import wikirmi.common.dto.*;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * No lost updates: N editors each acquire→read→save in a retry loop. Because the edit-lease
 * serializes writers, the final version must equal exactly N and the history must hold N
 * revisions — no concurrent save is silently lost.
 */
public class LostUpdateTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM);
        s.createPage("Doc", "v0", "admin");
        int n = 50;

        ExecutorService es = Executors.newFixedThreadPool(8);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final String tok = "t" + i, nm = "u" + i;
            fs.add(es.submit(() -> {
                boolean done = false;
                while (!done) {
                    try {
                        s.acquireEditLock("Doc", tok, nm);
                        Dto.Page cur = s.getPage("Doc");
                        s.savePage("Doc", tok, nm, "edit by " + nm, cur.getVersion());
                        done = true;
                    } catch (Exception retry) {
                        try { Thread.sleep(2); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }));
        }
        for (Future<?> f : fs) f.get();
        es.shutdownNow();

        Dto.Page p = s.getPage("Doc");
        Assert.eq(p.getVersion(), n, "version incremented exactly N times (no lost update)");
        Assert.eq(s.getHistory("Doc").size(), n, "history contains exactly N revisions");
    }
}
