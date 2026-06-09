package wikirmi.test;

import java.util.List;

import wikirmi.common.Role;
import wikirmi.common.exceptions.WikiException;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * SCENARIUSZ 4 — usunięcie użytkownika, który właśnie edytuje stronę.
 *
 * Pokazuje, że usunięcie konta jest "czyste": usuwany użytkownik zostaje wylogowany
 * (zwolnione pozwolenie semafora), a wszystkie jego blokady edycji są natychmiast
 * zwalniane, więc ktoś inny może od razu przejąć stronę.
 */
public class Demo4_UsuniecieEdytujacegoUzytkownika {
    public static void run() throws Exception {
        WikiStore serwer = new WikiStore(30000, Clock.SYSTEM, 5);
        String sol = PasswordHasher.newSalt();
        serwer.createUser("bob", sol, PasswordHasher.hash("haslo", sol), Role.USER);
        String tokenBoba = serwer.openSession("bob", Role.USER);     // Bob się loguje (sesja + semafor)
        serwer.createPage("Notatka", "treść", "admin");

        // 1. Bob wchodzi w edycję strony.
        serwer.acquireEditLock("Notatka", tokenBoba, "bob");
        System.out.println("    [1] Bob (zalogowany) edytuje 'Notatka'. Online: " + serwer.onlineUsernames());
        Assert.isTrue(serwer.onlineUsernames().contains("bob"), "Bob jest online i trzyma blokadę");

        // 2. Administrator usuwa konto Boba (serwer robi: deleteUser + kickUser).
        serwer.deleteUser("bob");
        List<String> zwolnione = serwer.kickUser("bob");
        System.out.println("    [2] Admin usunął konto Boba. Zwolnione blokady: " + zwolnione);

        // 3. Skutki: Bob wylogowany, jego blokada zwolniona, sesja unieważniona.
        Assert.isTrue(zwolnione.contains("Notatka"), "blokada Boba na 'Notatka' została zwolniona");
        Assert.isTrue(!serwer.onlineUsernames().contains("bob"), "Bob został wylogowany (zniknął z listy online)");
        Assert.throwsEx(WikiException.class,
                () -> serwer.requireSession(tokenBoba),
                "stara sesja Boba już nie działa");
        System.out.println("    [3] Bob wylogowany, sesja unieważniona, blokada zdjęta.");

        // 4. Ktoś inny może teraz spokojnie edytować tę stronę.
        Assert.isTrue(serwer.acquireEditLock("Notatka", "token-ali", "ala") != null,
                "po usunięciu Boba inny użytkownik może edytować 'Notatka'");
        System.out.println("    [4] Ala może teraz edytować 'Notatka' — strona wolna.");
    }
}
