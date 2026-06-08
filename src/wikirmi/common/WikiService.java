package wikirmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import wikirmi.common.dto.*;
import wikirmi.common.exceptions.WikiException;

/**
 * The remote contract exposed by the wiki server. Every method may fail with a
 * {@link RemoteException} (network/RMI failure) or a {@link WikiException} (domain error).
 * All methods except {@link #login} take a session token obtained from {@code login}.
 */
public interface WikiService extends Remote {

    // --- Authentication ---
    Dto.Session login(String username, String password) throws RemoteException, WikiException;
    void logout(String token) throws RemoteException, WikiException;

    // --- Admin only: the administrator creates page skeletons and user accounts ---
    void createUser(String token, String username, String password, Role role) throws RemoteException, WikiException;
    void deleteUser(String token, String username) throws RemoteException, WikiException;
    List<Dto.User> listUsers(String token) throws RemoteException, WikiException;
    void createPage(String token, String title, String initialContent) throws RemoteException, WikiException;
    void deletePage(String token, String title) throws RemoteException, WikiException;

    // --- Browse / search (any authenticated user) ---
    List<Dto.PageSummary> listPages(String token) throws RemoteException, WikiException;
    List<Dto.PageSummary> searchPages(String token, String query) throws RemoteException, WikiException;
    Dto.Page getPage(String token, String title) throws RemoteException, WikiException;
    List<String> listOnlineUsers(String token) throws RemoteException, WikiException;

    // --- Account ---
    void changePassword(String token, String oldPassword, String newPassword) throws RemoteException, WikiException;

    // --- Editing (lock-based) ---
    Dto.LockInfo acquireEditLock(String token, String title) throws RemoteException, WikiException;
    void renewEditLock(String token, String title) throws RemoteException, WikiException;
    void releaseEditLock(String token, String title) throws RemoteException, WikiException;
    Dto.Page savePage(String token, String title, String newContent, long baseVersion) throws RemoteException, WikiException;

    // --- History ---
    List<Dto.Revision> getHistory(String token, String title) throws RemoteException, WikiException;
    Dto.Page getRevision(String token, String title, int revisionIndex) throws RemoteException, WikiException;
    /** Restore a page to an earlier revision (written as a new revision). */
    Dto.Page restoreRevision(String token, String title, int revisionIndex) throws RemoteException, WikiException;

    // --- Admin: lock management ---
    /** Force-release any edit-lock on a page (admin only). */
    void forceUnlock(String token, String title) throws RemoteException, WikiException;

    // --- Live notifications (server -> client callbacks) ---
    void subscribe(String token, WikiClientCallback client) throws RemoteException, WikiException;
    void unsubscribe(String token, WikiClientCallback client) throws RemoteException, WikiException;
}
