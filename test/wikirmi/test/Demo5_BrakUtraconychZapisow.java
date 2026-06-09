package wikirmi.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import wikirmi.common.dto.Dto;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * SCENARIUSZ 5 — brak utraconych zapisów (lost update).
 *
 * 50 wątków-edytorów próbuje zapisywać tę samą stronę. Dzięki blokadzie edycji
 * zapisy ustawiają się w kolejce (serializują), więc numer wersji końcowej to
 * DOKŁADNIE 50 — żaden zapis nie nadpisuje innego i nic nie ginie.
 */
public class Demo5_BrakUtraconychZapisow {
    public static void run() throws Exception {
        WikiStore serwer = new WikiStore(30000, Clock.SYSTEM);
        serwer.createPage("Dokument", "wersja 0", "admin");

        int liczbaEdytorow = 50;
        ExecutorService pula = Executors.newFixedThreadPool(8);
        List<Future<?>> zadania = new ArrayList<>();

        for (int i = 0; i < liczbaEdytorow; i++) {
            final String token = "token" + i, nazwa = "user" + i;
            zadania.add(pula.submit(() -> {
                boolean zapisano = false;
                while (!zapisano) {                                  // każdy edytor próbuje aż się uda
                    try {
                        serwer.acquireEditLock("Dokument", token, nazwa);     // zajmij (jeśli wolne)
                        Dto.Page biezaca = serwer.getPage("Dokument");
                        serwer.savePage("Dokument", token, nazwa, "zmiana od " + nazwa, biezaca.getVersion());
                        zapisano = true;
                    } catch (Exception stronaZajeta) {
                        try { Thread.sleep(2); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }));
        }
        for (Future<?> f : zadania) f.get();                        // poczekaj na wszystkich
        pula.shutdownNow();

        Dto.Page wynik = serwer.getPage("Dokument");
        System.out.println("    50 edytorów zapisało po kolei → wersja końcowa: " + wynik.getVersion()
                + ", wpisów w historii: " + serwer.getHistory("Dokument").size());
        Assert.eq(wynik.getVersion(), liczbaEdytorow, "wersja = 50 (każdy zapis policzony, nic nie zgubione)");
        Assert.eq(serwer.getHistory("Dokument").size(), liczbaEdytorow, "historia ma dokładnie 50 wpisów");
    }
}
