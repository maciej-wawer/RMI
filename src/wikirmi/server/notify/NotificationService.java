package wikirmi.server.notify;

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import wikirmi.common.WikiClientCallback;
import wikirmi.common.dto.LockInfoDTO;
import wikirmi.common.dto.PageSummaryDTO;

/**
 * Delivers server→client push notifications over RMI callbacks. Each event is dispatched on a
 * background {@link ExecutorService}, so a slow or dead client never blocks the thread that made
 * the edit. A client whose callback throws {@link RemoteException} is dropped automatically.
 */
public class NotificationService {

    private final ConcurrentHashMap<String, WikiClientCallback> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public NotificationService() {
        ThreadFactory daemonFactory = r -> {
            Thread t = new Thread(r, "notify-dispatch");
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(4, daemonFactory);
    }

    public void subscribe(String token, WikiClientCallback client) { subscribers.put(token, client); }

    public void unsubscribe(String token) { subscribers.remove(token); }

    public int subscriberCount() { return subscribers.size(); }

    public void shutdown() { executor.shutdownNow(); }

    // ----- event API (called by WikiServiceImpl) -----
    public void pageCreated(PageSummaryDTO page) { broadcast(cb -> cb.onPageCreated(page)); }
    public void pageChanged(PageSummaryDTO page) { broadcast(cb -> cb.onPageChanged(page)); }
    public void pageDeleted(String title)        { broadcast(cb -> cb.onPageDeleted(title)); }
    public void lockChanged(String title, LockInfoDTO lock) { broadcast(cb -> cb.onLockChanged(title, lock)); }
    public void presenceChanged()                { broadcast(WikiClientCallback::onPresenceChanged); }

    private interface Push { void send(WikiClientCallback cb) throws RemoteException; }

    private void broadcast(Push push) {
        for (Map.Entry<String, WikiClientCallback> e : subscribers.entrySet()) {
            final String token = e.getKey();
            final WikiClientCallback cb = e.getValue();
            executor.submit(() -> {
                try {
                    push.send(cb);
                } catch (RemoteException dead) {
                    subscribers.remove(token, cb);          // client gone -> drop it
                } catch (RuntimeException ignored) {
                    // never let one bad client break dispatch
                }
            });
        }
    }
}
