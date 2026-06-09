package wikirmi.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * SCENARIUSZ 3 — dwóch (i więcej) użytkowników NIE wejdzie naraz do edycji tej samej strony.
 *
 * To główna ochrona przed stanem wyścigu (race condition). Najpierw prosty przypadek
 * dwóch osób, a potem mocny dowód: 10 wątków rusza jednocześnie i tylko JEDEN wygrywa.
 */
public class Demo3_DwochNaRazNieWejdzie {
    public static void run() throws Exception {
        WikiStore serwer = new WikiStore(30000, Clock.SYSTEM);
        serwer.createPage("Wspólna", "treść", "admin");

        // --- część A: dwóch użytkowników ---
        serwer.acquireEditLock("Wspólna", "token-ali", "ala");
        System.out.println("    [A1] Ala weszła w edycję 'Wspólna'.");

        PageLockedException odmowa = Assert.throwsEx(PageLockedException.class,
                () -> serwer.acquireEditLock("Wspólna", "token-boba", "bob"),
                "Bob NIE wejdzie w tym samym czasie co Ala");
        System.out.println("    [A2] Bob odrzucony: \"" + odmowa.getMessage() + "\"");

        serwer.releaseEditLock("Wspólna", "token-ali");
        Assert.isTrue(serwer.acquireEditLock("Wspólna", "token-boba", "bob") != null,
                "dopiero gdy Ala zwolni blokadę, Bob może wejść");
        System.out.println("    [A3] Ala zwolniła blokadę → Bob wchodzi w edycję.");
        serwer.releaseEditLock("Wspólna", "token-boba");

        // --- część B: mocny dowód — 10 wątków rusza jednocześnie ---
        int liczbaWatkow = 10;
        CountDownLatch start = new CountDownLatch(1);          // wspólny "strzał startowy"
        CountDownLatch koniec = new CountDownLatch(liczbaWatkow);
        AtomicInteger weszli = new AtomicInteger();
        AtomicInteger odrzuceni = new AtomicInteger();
        ExecutorService pula = Executors.newFixedThreadPool(liczbaWatkow);

        for (int i = 0; i < liczbaWatkow; i++) {
            final String token = "token" + i, nazwa = "user" + i;
            pula.submit(() -> {
                try {
                    start.await();                            // wszyscy czekają na start
                    serwer.acquireEditLock("Wspólna", token, nazwa);
                    weszli.incrementAndGet();                 // ten wątek zdobył blokadę
                } catch (PageLockedException e) {
                    odrzuceni.incrementAndGet();              // ci zobaczyli stronę już zajętą
                } catch (Exception ignored) {
                } finally {
                    koniec.countDown();
                }
            });
        }
        start.countDown();                                    // START — wszystkie wątki ruszają naraz
        koniec.await(5, TimeUnit.SECONDS);
        pula.shutdownNow();

        System.out.println("    [B] 10 wątków naraz → weszło: " + weszli.get()
                + ", odrzuconych: " + odrzuceni.get());
        Assert.eq(weszli.get(), 1, "z 10 jednoczesnych prób DOKŁADNIE JEDNA zakłada blokadę");
        Assert.eq(odrzuceni.get(), liczbaWatkow - 1, "pozostałe 9 dostaje PageLockedException");
    }
}
