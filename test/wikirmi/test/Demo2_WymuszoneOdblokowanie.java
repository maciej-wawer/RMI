package wikirmi.test;

import wikirmi.common.dto.Dto;
import wikirmi.common.exceptions.PageLockedException;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * SCENARIUSZ 2 — administrator wymusza odblokowanie strony.
 *
 * Gdy ktoś trzyma blokadę edycji (np. zawiesił się), administrator może ją zdjąć,
 * dzięki czemu inny użytkownik od razu wchodzi w edycję.
 */
public class Demo2_WymuszoneOdblokowanie {
    public static void run() throws Exception {
        WikiStore serwer = new WikiStore(30000, Clock.SYSTEM);
        serwer.createPage("Regulamin", "treść", "admin");

        // 1. Ala zakłada blokadę edycji.
        serwer.acquireEditLock("Regulamin", "token-ali", "ala");
        System.out.println("    [1] Ala trzyma blokadę edycji 'Regulamin'.");

        // 2. Bob NIE wejdzie — strona jest zajęta.
        PageLockedException odmowa = Assert.throwsEx(PageLockedException.class,
                () -> serwer.acquireEditLock("Regulamin", "token-boba", "bob"),
                "Bob nie wejdzie, dopóki Ala trzyma blokadę");
        System.out.println("    [2] Bob odrzucony: \"" + odmowa.getMessage() + "\"");

        // 3. Administrator WYMUSZA odblokowanie.
        serwer.forceUnlock("Regulamin");
        System.out.println("    [3] Admin wymusił odblokowanie strony 'Regulamin'.");

        // 4. Teraz Bob spokojnie wchodzi w edycję.
        Dto.LockInfo blokadaBoba = serwer.acquireEditLock("Regulamin", "token-boba", "bob");
        Assert.isTrue(blokadaBoba != null, "po wymuszeniu odblokowania Bob zakłada blokadę");
        System.out.println("    [4] Bob może teraz edytować (blokada należy do: " + blokadaBoba.getHolder() + ").");
    }
}
