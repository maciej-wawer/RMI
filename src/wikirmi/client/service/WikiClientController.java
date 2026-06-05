package wikirmi.client.service;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

import wikirmi.common.Protocol;
import wikirmi.common.Role;
import wikirmi.common.WikiClientCallback;
import wikirmi.common.WikiService;
import wikirmi.common.dto.*;
import wikirmi.common.exceptions.WikiException;

/**
 * The ONLY class on the client that talks RMI. It owns the remote stub and the session token and
 * exposes plain methods to the GUI, keeping the presentation layer free of any networking code.
 */
public class WikiClientController {
    private WikiService stub;
    private String token;
    private UserDTO user;

    /** Looks up the remote service in the registry. */
    public void connect(String host, int port) throws RemoteException, java.rmi.NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        stub = (WikiService) registry.lookup(Protocol.SERVICE_NAME);
    }

    public UserDTO login(String username, String password) throws RemoteException, WikiException {
        SessionDTO s = stub.login(username, password);
        this.token = s.getToken();
        this.user = s.getUser();
        return user;
    }

    public void logout() {
        if (stub == null || token == null) return;
        try { stub.logout(token); } catch (Exception ignored) { }
        token = null;
        user = null;
    }

    public UserDTO currentUser() { return user; }
    public boolean isAdmin() { return user != null && user.getRole() == Role.ADMIN; }

    // ----- browse / search -----
    public List<PageSummaryDTO> listPages() throws RemoteException, WikiException { return stub.listPages(token); }
    public List<PageSummaryDTO> searchPages(String q) throws RemoteException, WikiException { return stub.searchPages(token, q); }
    public PageDTO getPage(String title) throws RemoteException, WikiException { return stub.getPage(token, title); }
    public List<String> listOnlineUsers() throws RemoteException, WikiException { return stub.listOnlineUsers(token); }
    public void changePassword(String oldPw, String newPw) throws RemoteException, WikiException { stub.changePassword(token, oldPw, newPw); }

    // ----- editing -----
    public LockInfoDTO acquireEditLock(String title) throws RemoteException, WikiException { return stub.acquireEditLock(token, title); }
    public void renewEditLock(String title) throws RemoteException, WikiException { stub.renewEditLock(token, title); }
    public void releaseEditLock(String title) throws RemoteException, WikiException { stub.releaseEditLock(token, title); }
    public PageDTO savePage(String title, String content, long baseVersion) throws RemoteException, WikiException {
        return stub.savePage(token, title, content, baseVersion);
    }

    // ----- history -----
    public List<RevisionDTO> getHistory(String title) throws RemoteException, WikiException { return stub.getHistory(token, title); }
    public PageDTO getRevision(String title, int idx) throws RemoteException, WikiException { return stub.getRevision(token, title, idx); }
    public PageDTO restoreRevision(String title, int idx) throws RemoteException, WikiException { return stub.restoreRevision(token, title, idx); }
    public void forceUnlock(String title) throws RemoteException, WikiException { stub.forceUnlock(token, title); }

    // ----- admin -----
    public void createPage(String title, String content) throws RemoteException, WikiException { stub.createPage(token, title, content); }
    public void deletePage(String title) throws RemoteException, WikiException { stub.deletePage(token, title); }
    public void createUser(String username, String password, Role role) throws RemoteException, WikiException { stub.createUser(token, username, password, role); }
    public void deleteUser(String username) throws RemoteException, WikiException { stub.deleteUser(token, username); }
    public List<UserDTO> listUsers() throws RemoteException, WikiException { return stub.listUsers(token); }

    // ----- notifications -----
    public void subscribe(WikiClientCallback cb) throws RemoteException, WikiException { stub.subscribe(token, cb); }
    public void unsubscribe(WikiClientCallback cb) throws RemoteException, WikiException { stub.unsubscribe(token, cb); }
}
