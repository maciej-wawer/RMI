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
import wikirmi.server.auth.SessionManager;
import wikirmi.server.model.User;
import wikirmi.server.notify.NotificationService;
import wikirmi.server.store.WikiStore;

/**
 * RMI-facing implementation of {@link WikiService}. Thin layer: authenticate/authorize the caller,
 * delegate to {@link WikiStore}, then persist and push notifications on mutations. All concurrency
 * control lives in {@link WikiStore}; this class adds no locks of its own.
 */
public class WikiServiceImpl extends UnicastRemoteObject implements WikiService {
    private static final long serialVersionUID = 1L;

    private final WikiStore store;
    private final SessionManager sessions;
    private final NotificationService notify;
    private final Runnable persist;

    public WikiServiceImpl(WikiStore store, SessionManager sessions, NotificationService notify, Runnable persist)
            throws RemoteException {
        super();
        this.store = store;
        this.sessions = sessions;
        this.notify = notify;
        this.persist = persist;
    }

    // ------------------------------------------------------------ auth
    @Override
    public SessionDTO login(String username, String password) throws RemoteException, WikiException {
        User user = store.getUser(username);
        if (user == null || !PasswordHasher.verify(password, user.salt(), user.hash()))
            throw new AuthenticationException("Błędny login lub hasło.");
        String token = sessions.open(username, user.role());
        return new SessionDTO(token, new UserDTO(username, user.role()));
    }

    @Override
    public void logout(String token) throws RemoteException, WikiException {
        notify.unsubscribe(token);
        sessions.close(token);
    }

    // ------------------------------------------------------------ admin: users
    @Override
    public void createUser(String token, String username, String password, Role role)
            throws RemoteException, WikiException {
        sessions.requireAdmin(token);
        if (password == null || password.length() < 3)
            throw new ValidationException("Hasło musi mieć co najmniej 3 znaki.");
        if (role == null) throw new ValidationException("Rola jest wymagana.");
        String salt = PasswordHasher.newSalt();
        store.createUser(username, salt, PasswordHasher.hash(password, salt), role);
        persist.run();
    }

    @Override
    public void deleteUser(String token, String username) throws RemoteException, WikiException {
        SessionManager.Session s = sessions.requireAdmin(token);
        if (s.username.equals(username)) throw new ValidationException("Nie można usunąć własnego konta.");
        store.deleteUser(username);
        persist.run();
    }

    @Override
    public List<UserDTO> listUsers(String token) throws RemoteException, WikiException {
        sessions.requireAdmin(token);
        return store.listUsers();
    }

    // ------------------------------------------------------------ admin: pages
    @Override
    public void createPage(String token, String title, String initialContent)
            throws RemoteException, WikiException {
        SessionManager.Session s = sessions.requireAdmin(token);
        PageDTO p = store.createPage(title, initialContent, s.username);
        persist.run();
        notify.pageCreated(new PageSummaryDTO(p.getTitle(), p.getVersion(), null, p.getLastModified()));
    }

    @Override
    public void deletePage(String token, String title) throws RemoteException, WikiException {
        sessions.requireAdmin(token);
        store.deletePage(title);
        persist.run();
        notify.pageDeleted(title);
    }

    // ------------------------------------------------------------ browse / search
    @Override
    public List<PageSummaryDTO> listPages(String token) throws RemoteException, WikiException {
        sessions.require(token);
        return store.listPages();
    }

    @Override
    public List<PageSummaryDTO> searchPages(String token, String query) throws RemoteException, WikiException {
        sessions.require(token);
        return store.search(query);
    }

    @Override
    public PageDTO getPage(String token, String title) throws RemoteException, WikiException {
        sessions.require(token);
        return store.getPage(title);
    }

    // ------------------------------------------------------------ editing (lock-based)
    @Override
    public LockInfoDTO acquireEditLock(String token, String title) throws RemoteException, WikiException {
        SessionManager.Session s = sessions.require(token);
        LockInfoDTO lock = store.acquireEditLock(title, s.token, s.username);
        notify.lockChanged(title, lock);
        return lock;
    }

    @Override
    public void renewEditLock(String token, String title) throws RemoteException, WikiException {
        SessionManager.Session s = sessions.require(token);
        store.renewEditLock(title, s.token);
    }

    @Override
    public void releaseEditLock(String token, String title) throws RemoteException, WikiException {
        SessionManager.Session s = sessions.require(token);
        store.releaseEditLock(title, s.token);
        notify.lockChanged(title, null);
    }

    @Override
    public PageDTO savePage(String token, String title, String newContent, long baseVersion)
            throws RemoteException, WikiException {
        SessionManager.Session s = sessions.require(token);
        PageDTO p = store.savePage(title, s.token, s.username, newContent, baseVersion);
        persist.run();
        notify.pageChanged(new PageSummaryDTO(p.getTitle(), p.getVersion(), null, p.getLastModified()));
        notify.lockChanged(title, null);                    // editing finished -> page free again
        return p;
    }

    // ------------------------------------------------------------ history
    @Override
    public List<RevisionDTO> getHistory(String token, String title) throws RemoteException, WikiException {
        sessions.require(token);
        return store.getHistory(title);
    }

    @Override
    public PageDTO getRevision(String token, String title, int revisionIndex)
            throws RemoteException, WikiException {
        sessions.require(token);
        return store.getRevision(title, revisionIndex);
    }

    // ------------------------------------------------------------ notifications
    @Override
    public void subscribe(String token, WikiClientCallback client) throws RemoteException, WikiException {
        sessions.require(token);
        notify.subscribe(token, client);
    }

    @Override
    public void unsubscribe(String token, WikiClientCallback client) throws RemoteException, WikiException {
        sessions.require(token);
        notify.unsubscribe(token);
    }
}
