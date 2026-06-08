package wikirmi.test;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import wikirmi.common.Role;
import wikirmi.common.WikiService;
import wikirmi.common.dto.*;
import wikirmi.server.WikiServiceImpl;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/**
 * Standalone (not part of TestRunner): exercises a real RMI round-trip through an in-process
 * registry. Run with: java -cp out wikirmi.test.RmiSmoke
 */
public class RmiSmoke {
    public static void main(String[] args) throws Exception {
        int port = 1100;                                      // non-default to avoid clashing with a live server

        WikiStore store = new WikiStore(30000, Clock.SYSTEM);
        String salt = PasswordHasher.newSalt();
        store.addUser(new User("admin", salt, PasswordHasher.hash("admin123", salt), Role.ADMIN));
        NotificationService notify = new NotificationService();
        WikiServiceImpl impl = new WikiServiceImpl(store, notify, () -> {});

        Registry reg = LocateRegistry.createRegistry(port);
        reg.rebind("WikiService", impl);

        WikiService stub = (WikiService) LocateRegistry.getRegistry("localhost", port).lookup("WikiService");
        Dto.Session session = stub.login("admin", "admin123");
        stub.createPage(session.getToken(), "Test", "treść testowa ąęś");
        Dto.Page p = stub.getPage(session.getToken(), "Test");

        if (!"treść testowa ąęś".equals(p.getContent()))
            throw new AssertionError("content mismatch over RMI: " + p.getContent());

        System.out.println("RMI round-trip OK: page='" + p.getTitle() + "' content='" + p.getContent() + "'");
        notify.shutdown();
        System.exit(0);                                       // RMI keeps the JVM alive otherwise
    }
}
