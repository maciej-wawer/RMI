package wikirmi.test;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.swing.SwingUtilities;

import wikirmi.client.gui.AdminDialog;
import wikirmi.client.gui.EditDialog;
import wikirmi.client.gui.HistoryDialog;
import wikirmi.client.gui.MainFrame;
import wikirmi.client.service.WikiClientController;
import wikirmi.common.Role;
import wikirmi.common.dto.PageDTO;
import wikirmi.server.WikiServiceImpl;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.auth.SessionManager;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Builds every Swing frame/dialog against a live server on the EDT to catch layout/wiring bugs.
 * Does NOT call setVisible, so nothing appears on screen. Run with:
 *   java -cp out wikirmi.test.GuiConstructionSmoke
 */
public class GuiConstructionSmoke {
    public static void main(String[] args) throws Exception {
        int port = 1102;
        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        String salt = PasswordHasher.newSalt();
        store.addUser(new User("admin", salt, PasswordHasher.hash("admin123", salt), Role.ADMIN));
        SessionManager sessions = new SessionManager(10);
        NotificationService notify = new NotificationService();
        WikiServiceImpl impl = new WikiServiceImpl(store, sessions, notify, () -> { });
        Registry reg = LocateRegistry.createRegistry(port);
        reg.rebind("WikiService", impl);

        WikiClientController c = new WikiClientController();
        c.connect("localhost", port);
        c.login("admin", "admin123");
        c.createPage("P1", "treść strony");
        final PageDTO page = c.getPage("P1");

        final Throwable[] err = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainFrame mf = new MainFrame(c, c.currentUser());
                new EditDialog(mf, c, page).dispose();
                new HistoryDialog(mf, c, "P1").dispose();
                new AdminDialog(mf, c).dispose();
                new wikirmi.client.gui.ChangePasswordDialog(mf, c).dispose();
                new wikirmi.client.gui.NewPageDialog(mf, c).dispose();
                mf.dispose();
            } catch (Throwable t) {
                err[0] = t;
            }
        });

        if (err[0] != null) {
            err[0].printStackTrace();
            throw new AssertionError("GUI construction failed: " + err[0]);
        }
        System.out.println("GUI CONSTRUCTION OK: LoginFrame/MainFrame/EditDialog/HistoryDialog/AdminDialog built cleanly.");
        notify.shutdown();
        System.exit(0);
    }
}
