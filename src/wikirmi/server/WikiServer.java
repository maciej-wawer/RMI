package wikirmi.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import wikirmi.server.auth.SessionManager;
import wikirmi.server.daemon.LockReaperDaemon;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.Clock;
import wikirmi.server.store.JsonPersistence;
import wikirmi.server.store.WikiStore;

/**
 * Server entry point: builds the store (loading or seeding data), creates the RMI registry
 * in-process, binds the service, starts the lock-reaper daemon, and installs a shutdown hook
 * that saves data on exit.
 */
public class WikiServer {

    public static void main(String[] args) throws Exception {
        Path dataFile = Paths.get(ServerConfig.DATA_FILE);

        WikiStore store = new WikiStore(ServerConfig.LEASE_MS, Clock.SYSTEM);
        JsonPersistence.loadOrSeed(store, dataFile, ServerConfig.DEFAULT_ADMIN, ServerConfig.DEFAULT_ADMIN_PASSWORD);

        SessionManager sessions = new SessionManager(ServerConfig.MAX_CLIENTS);
        NotificationService notify = new NotificationService();
        Runnable persist = () -> JsonPersistence.save(store, dataFile);

        WikiServiceImpl impl = new WikiServiceImpl(store, sessions, notify, persist);

        Registry registry = LocateRegistry.createRegistry(ServerConfig.REGISTRY_PORT);
        registry.rebind(ServerConfig.SERVICE_NAME, impl);

        LockReaperDaemon reaper = new LockReaperDaemon(store, notify, ServerConfig.REAP_MS);
        reaper.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nZatrzymywanie serwera — zapisywanie danych...");
            try { persist.run(); } catch (Exception e) { System.err.println("Błąd zapisu: " + e.getMessage()); }
            notify.shutdown();
            reaper.stop();
        }));

        System.out.println("=== Serwer WikiRMI ===");
        System.out.println("Gotowy na porcie " + ServerConfig.REGISTRY_PORT
                + ", usługa '" + ServerConfig.SERVICE_NAME + "'.");
        System.out.println("Plik danych: " + dataFile.toAbsolutePath());
        System.out.println("Limit klientów: " + ServerConfig.MAX_CLIENTS);
        System.out.println("Domyślny administrator: " + ServerConfig.DEFAULT_ADMIN
                + " / " + ServerConfig.DEFAULT_ADMIN_PASSWORD);
        System.out.println("Naciśnij Ctrl+C, aby zatrzymać serwer.");
        // RMI's exported objects keep the JVM alive; main() may return.
    }
}
