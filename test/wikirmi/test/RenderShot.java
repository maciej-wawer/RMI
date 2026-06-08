package wikirmi.test;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import wikirmi.client.gui.MainFrame;
import wikirmi.client.service.WikiClientController;
import wikirmi.common.Role;
import wikirmi.server.WikiServiceImpl;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Renders the real (Nimbus-themed) MainFrame against a seeded server and captures it to a PNG,
 * so the layout/colors can be inspected without screen-control of the Java window.
 * Run: java -cp out wikirmi.test.RenderShot
 */
public class RenderShot {
    public static void main(String[] args) throws Exception {
        wikirmi.client.WikiClient.applyLookAndFeel();   // ten sam wygląd (FlatLaf) co w aplikacji

        int port = 1103;
        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        String salt = PasswordHasher.newSalt();
        store.addUser(new User("admin", salt, PasswordHasher.hash("admin123", salt), Role.ADMIN));
        NotificationService notify = new NotificationService();
        WikiServiceImpl impl = new WikiServiceImpl(store, notify, () -> { });
        Registry reg = LocateRegistry.createRegistry(port);
        reg.rebind("WikiService", impl);

        WikiClientController c = new WikiClientController();
        c.connect("localhost", port);
        c.login("admin", "admin123");
        c.createPage("Strona główna",
                "# Witaj w WikiRMI\nTo jest **przykładowa** strona z formatowaniem *Markdown*.\n"
                        + "- pierwszy punkt\n- drugi punkt\nZobacz też [[Instrukcja]].");
        c.createPage("Instrukcja", "## Instrukcja\nNaciśnij **Edytuj**, aby zmienić treść strony.");

        final MainFrame[] mf = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { mf[0] = new MainFrame(c, c.currentUser()); mf[0].setVisible(true); mf[0].toFront(); });
        Thread.sleep(1100);                                     // async page list
        SwingUtilities.invokeAndWait(() -> mf[0].selectFirstPage());
        Thread.sleep(900);                                      // async content + render
        SwingUtilities.invokeAndWait(() -> mf[0].toFront());
        Thread.sleep(250);

        final Rectangle[] bounds = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> bounds[0] = mf[0].getBounds());
        Robot robot = new Robot();
        ImageIO.write(robot.createScreenCapture(bounds[0]), "png", new File("ui-main.png"));
        System.out.println("WROTE ui-main.png");

        // also capture the "new user" dialog (shown non-modally just for the shot)
        final wikirmi.client.gui.NewUserDialog[] dlg = new wikirmi.client.gui.NewUserDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dlg[0] = new wikirmi.client.gui.NewUserDialog(mf[0], c, () -> { });
            dlg[0].setModal(false);
            dlg[0].setVisible(true);
            dlg[0].toFront();
        });
        Thread.sleep(400);
        final Rectangle[] db = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> db[0] = dlg[0].getBounds());
        ImageIO.write(robot.createScreenCapture(db[0]), "png", new File("ui-newuser.png"));
        System.out.println("WROTE ui-newuser.png");

        // capture the admin panel (verify buttons are not covered)
        final wikirmi.client.gui.AdminDialog[] adm = new wikirmi.client.gui.AdminDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            adm[0] = new wikirmi.client.gui.AdminDialog(mf[0], c);
            adm[0].setModal(false);
            adm[0].setVisible(true);
            adm[0].toFront();
        });
        Thread.sleep(500);
        final Rectangle[] ab = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> ab[0] = adm[0].getBounds());
        ImageIO.write(robot.createScreenCapture(ab[0]), "png", new File("ui-admin.png"));
        System.out.println("WROTE ui-admin.png");

        SwingUtilities.invokeAndWait(() -> { adm[0].dispose(); dlg[0].dispose(); mf[0].dispose(); });
        notify.shutdown();
        System.exit(0);
    }
}
