package wikirmi.test;

import wikirmi.common.exceptions.WikiException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * SCENARIUSZ 1 — usunięcie strony, którą ktoś właśnie edytuje.
 *
 * Pokazuje, że system radzi sobie z konfliktem: admin może usunąć stronę w trakcie
 * edycji, a edytujący użytkownik przy próbie zapisu dostaje czytelny błąd zamiast awarii.
 */
public class Demo1_UsuwanieEdytowanejStrony {
    public static void run() throws Exception {
        WikiStore serwer = new WikiStore(30000, Clock.SYSTEM);
        serwer.createPage("Artykuł", "treść początkowa", "admin");

        // 1. Ala wchodzi w edycję — zakłada blokadę na stronie.
        serwer.acquireEditLock("Artykuł", "token-ali", "ala");
        System.out.println("    [1] Ala edytuje stronę 'Artykuł' (trzyma blokadę).");

        // 2. Administrator usuwa stronę W TRAKCIE edycji.
        serwer.deletePage("Artykuł");
        System.out.println("    [2] Admin usunął stronę 'Artykuł' podczas edycji Ali.");

        // 3. Ala próbuje zapisać — strona już nie istnieje → czytelny błąd (bez awarii).
        boolean zapisOdrzucony = false;
        try {
            serwer.savePage("Artykuł", "token-ali", "ala", "moje zmiany", 0);
        } catch (WikiException e) {
            zapisOdrzucony = true;
            System.out.println("    [3] Zapis Ali odrzucony: \"" + e.getMessage() + "\"");
        }
        Assert.isTrue(zapisOdrzucony, "zapis usuniętej strony jest odrzucany z czytelnym błędem");

        // 4. Odnowienie blokady też się nie uda (w GUI to zamyka okno edycji Ali).
        Assert.throwsEx(WikiException.class,
                () -> serwer.renewEditLock("Artykuł", "token-ali"),
                "odnowienie blokady usuniętej strony jest odrzucane");
        System.out.println("    [4] Odnowienie blokady też odrzucone — edytor Ali zostałby zamknięty.");
    }
}
