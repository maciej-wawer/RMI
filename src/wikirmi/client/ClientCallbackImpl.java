package wikirmi.client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import javax.swing.SwingUtilities;

import wikirmi.common.WikiClientCallback;
import wikirmi.common.dto.*;

/**
 * The client's remote callback object (server -> client). Exported via UnicastRemoteObject so the
 * server can invoke it. Every event is marshalled onto the Swing EDT before touching the UI.
 */
public class ClientCallbackImpl extends UnicastRemoteObject implements WikiClientCallback {
    private static final long serialVersionUID = 1L;
    private final WikiEvents events;

    public ClientCallbackImpl(WikiEvents events) throws RemoteException {
        super();
        this.events = events;
    }

    @Override public void onPageCreated(Dto.PageSummary page) { SwingUtilities.invokeLater(() -> events.pageCreated(page)); }
    @Override public void onPageChanged(Dto.PageSummary page) { SwingUtilities.invokeLater(() -> events.pageChanged(page)); }
    @Override public void onPageDeleted(String title)        { SwingUtilities.invokeLater(() -> events.pageDeleted(title)); }
    @Override public void onLockChanged(String title, Dto.LockInfo lock) { SwingUtilities.invokeLater(() -> events.lockChanged(title, lock)); }
    @Override public void onPresenceChanged()                { SwingUtilities.invokeLater(events::presenceChanged); }
}
