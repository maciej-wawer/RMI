package wikirmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageSummaryDTO;

/**
 * Remote interface implemented by the client so the server can push live updates
 * (the reverse RMI direction). Registered via {@link WikiService#subscribe}.
 */
public interface WikiClientCallback extends Remote {
    void onPageCreated(PageSummaryDTO page) throws RemoteException;
    void onPageChanged(PageSummaryDTO page) throws RemoteException;
    void onPageDeleted(String title) throws RemoteException;
    /** {@code lock == null} means the page was released or its lock expired. */
    void onLockChanged(String title, LockInfoDTO lock) throws RemoteException;
}
