package wikirmi.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import wikirmi.common.Role;
import wikirmi.common.WikiClientCallback;
import wikirmi.common.WikiService;
import wikirmi.common.dto.*;
import wikirmi.common.exceptions.*;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.WikiStore;

/**
 * ===========================================================================
 *  MECHANIZM RMI (zdalne wywoływanie metod) — strona serwera.
 *
 *  Tak jak w skrypcie:
 *    - interfejs zdalny WikiService DZIEDZICZY po java.rmi.Remote, a każda jego
 *      metoda deklaruje "throws RemoteException";
 *    - ta klasa implementacji DZIEDZICZY po UnicastRemoteObject i implementuje
 *      ten interfejs (zostaje wyeksportowana jako obiekt zdalny);
 *    - serwer rejestruje ją w rejestrze RMI (Naming.rebind), a klient pobiera
 *      "namiastkę" (stub) przez Naming.lookup i wywołuje metody jak lokalne —
 *      tyle że realnie wykonują się one na serwerze.
 *
 *  Rola tej warstwy: sprawdza uprawnienia (sesja/rola), deleguje do {@link WikiStore},
 *  a po zmianach zapisuje dane i rozsyła powiadomienia (callback RMI w drugą stronę).
 *  CAŁA synchronizacja jest w WikiStore — tutaj nie ma żadnych blokad.
 * ===========================================================================
 */
public class WikiServiceImpl extends UnicastRemoteObject implements WikiService {
    private static final long serialVersionUID = 1L;

    private final WikiStore store;
    private final NotificationService notify;
    private final Runnable persist;

    public WikiServiceImpl(WikiStore store, NotificationService notify, Runnable persist) throws RemoteException {
        super();
        this.store = store;
        this.notify = notify;
        this.persist = persist;
    }

    // ------------------------------------------------------------ logowanie
    @Override
    public Dto.Session login(String username, String password) throws RemoteException, WikiException {
        User user = store.getUser(username);
        if (user == null || !PasswordHasher.verify(password, user.salt(), user.hash()))
            throw new WikiException("Błędny login lub hasło.");
        String token = store.openSession(username, user.role());      // SEMAFOR: pobiera pozwolenie
        notify.presenceChanged();
        return new Dto.Session(token, new Dto.User(username, user.role()));
    }

    @Override
    public void logout(String token) throws RemoteException, WikiException {
        notify.unsubscribe(token);
        store.closeSession(token);                                     // SEMAFOR: oddaje pozwolenie
        notify.presenceChanged();
    }

    // ------------------------------------------------------------ admin: użytkownicy
    @Override
    public void createUser(String token, String username, String password, Role role)
            throws RemoteException, WikiException {
        store.requireAdmin(token);
        if (password == null || password.length() < 3)
            throw new WikiException("Hasło musi mieć co najmniej 3 znaki.");
        if (role == null) throw new WikiException("Rola jest wymagana.");
        String salt = PasswordHasher.newSalt();
        store.createUser(username, salt, PasswordHasher.hash(password, salt), role);
        persist.run();
    }

    @Override
    public void deleteUser(String token, String username) throws RemoteException, WikiException {
        WikiStore.Session s = store.requireAdmin(token);
        if (s.username.equals(username)) throw new WikiException("Nie można usunąć własnego konta.");
        store.deleteUser(username);
        // wyloguj usuniętego użytkownika i zwolnij jego blokady edycji; powiadom innych
        for (String title : store.kickUser(username)) notify.lockChanged(title, null);
        notify.presenceChanged();
        persist.run();
    }

    @Override
    public List<Dto.User> listUsers(String token) throws RemoteException, WikiException {
        store.requireAdmin(token);
        return store.listUsers();
    }

    // ------------------------------------------------------------ strony
    @Override
    public void createPage(String token, String title, String initialContent)
            throws RemoteException, WikiException {
        // Tworzyć strony może każdy zalogowany użytkownik (usuwanie tylko admin).
        WikiStore.Session s = store.requireSession(token);
        Dto.Page p = store.createPage(title, initialContent, s.username);
        persist.run();
        notify.pageCreated(new Dto.PageSummary(p.getTitle(), p.getVersion(), null, p.getLastModified()));
    }

    @Override
    public void deletePage(String token, String title) throws RemoteException, WikiException {
        store.requireAdmin(token);
        store.deletePage(title);
        persist.run();
        notify.pageDeleted(title);
    }

    // ------------------------------------------------------------ przeglądanie / wyszukiwanie
    @Override
    public List<Dto.PageSummary> listPages(String token) throws RemoteException, WikiException {
        store.requireSession(token);
        return store.listPages();
    }

    @Override
    public List<Dto.PageSummary> searchPages(String token, String query) throws RemoteException, WikiException {
        store.requireSession(token);
        return store.search(query);
    }

    @Override
    public Dto.Page getPage(String token, String title) throws RemoteException, WikiException {
        store.requireSession(token);
        return store.getPage(title);
    }

    @Override
    public List<String> listOnlineUsers(String token) throws RemoteException, WikiException {
        store.requireSession(token);
        return store.onlineUsernames();
    }

    @Override
    public void changePassword(String token, String oldPassword, String newPassword)
            throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        if (newPassword == null || newPassword.length() < 3)
            throw new WikiException("Nowe hasło musi mieć co najmniej 3 znaki.");
        User u = store.getUser(s.username);
        if (u == null || !PasswordHasher.verify(oldPassword, u.salt(), u.hash()))
            throw new WikiException("Aktualne hasło jest nieprawidłowe.");
        String salt = PasswordHasher.newSalt();
        store.updatePassword(s.username, salt, PasswordHasher.hash(newPassword, salt));
        persist.run();
    }

    // ------------------------------------------------------------ edycja (z blokadą)
    @Override
    public Dto.LockInfo acquireEditLock(String token, String title) throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        Dto.LockInfo lock = store.acquireEditLock(title, s.token, s.username);
        notify.lockChanged(title, lock);
        return lock;
    }

    @Override
    public void renewEditLock(String token, String title) throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        store.renewEditLock(title, s.token);
    }

    @Override
    public void releaseEditLock(String token, String title) throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        store.releaseEditLock(title, s.token);
        notify.lockChanged(title, null);
    }

    @Override
    public Dto.Page savePage(String token, String title, String newContent, long baseVersion)
            throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        Dto.Page p = store.savePage(title, s.token, s.username, newContent, baseVersion);
        persist.run();
        notify.pageChanged(new Dto.PageSummary(p.getTitle(), p.getVersion(), null, p.getLastModified()));
        notify.lockChanged(title, null);
        return p;
    }

    // ------------------------------------------------------------ historia
    @Override
    public List<Dto.Revision> getHistory(String token, String title) throws RemoteException, WikiException {
        store.requireSession(token);
        return store.getHistory(title);
    }

    @Override
    public Dto.Page getRevision(String token, String title, int revisionIndex)
            throws RemoteException, WikiException {
        store.requireSession(token);
        return store.getRevision(title, revisionIndex);
    }

    @Override
    public Dto.Page restoreRevision(String token, String title, int revisionIndex)
            throws RemoteException, WikiException {
        WikiStore.Session s = store.requireSession(token);
        Dto.Page p = store.restoreRevision(title, s.token, s.username, revisionIndex);
        persist.run();
        notify.pageChanged(new Dto.PageSummary(p.getTitle(), p.getVersion(), null, p.getLastModified()));
        notify.lockChanged(title, null);
        return p;
    }

    @Override
    public void forceUnlock(String token, String title) throws RemoteException, WikiException {
        store.requireAdmin(token);
        store.forceUnlock(title);
        notify.lockChanged(title, null);
    }

    // ------------------------------------------------------------ powiadomienia
    @Override
    public void subscribe(String token, WikiClientCallback client) throws RemoteException, WikiException {
        store.requireSession(token);
        notify.subscribe(token, client);
    }

    @Override
    public void unsubscribe(String token, WikiClientCallback client) throws RemoteException, WikiException {
        store.requireSession(token);
        notify.unsubscribe(token);
    }
}
