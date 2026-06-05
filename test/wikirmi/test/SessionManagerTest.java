package wikirmi.test;

import wikirmi.common.Role;
import wikirmi.common.exceptions.AuthenticationException;
import wikirmi.common.exceptions.AuthorizationException;
import wikirmi.server.auth.SessionManager;

/** Verifies session resolution and the Semaphore-enforced client cap. */
public class SessionManagerTest {
    public static void run() throws Exception {
        SessionManager sm = new SessionManager(2);                 // cap = 2 clients
        String t1 = sm.open("alice", Role.ADMIN);
        String t2 = sm.open("bob", Role.USER);
        Assert.eq(sm.require(t1).username, "alice", "resolve session token");
        Assert.eq(sm.require(t2).role, Role.USER, "resolve role");

        java.util.List<String> online = sm.onlineUsernames();
        Assert.isTrue(online.contains("alice") && online.contains("bob"), "online users are listed");
        Assert.eq(online.size(), 2, "two distinct users online");

        // permission model: USER passes the basic check (may create pages) but fails the admin check
        Assert.isTrue(sm.require(t2) != null, "USER passes the basic session check (can create pages)");
        Assert.throwsEx(AuthorizationException.class, () -> sm.requireAdmin(t2), "USER fails the admin check (cannot delete)");
        Assert.isTrue(sm.requireAdmin(t1) != null, "ADMIN passes the admin check");

        Assert.throwsEx(AuthenticationException.class,
                () -> sm.open("carol", Role.USER), "third login over the cap is rejected");

        sm.close(t1);                                              // frees one permit
        String t3 = sm.open("carol", Role.USER);                  // now succeeds
        Assert.eq(sm.require(t3).username, "carol", "permit reused after close");

        Assert.throwsEx(AuthenticationException.class,
                () -> sm.require("bogus-token"), "unknown token rejected");
    }
}
