package wikirmi.test;

import wikirmi.common.Role;
import wikirmi.common.exceptions.AuthenticationException;
import wikirmi.server.auth.SessionManager;

/** Verifies session resolution and the Semaphore-enforced client cap. */
public class SessionManagerTest {
    public static void run() throws Exception {
        SessionManager sm = new SessionManager(2);                 // cap = 2 clients
        String t1 = sm.open("alice", Role.ADMIN);
        String t2 = sm.open("bob", Role.USER);
        Assert.eq(sm.require(t1).username, "alice", "resolve session token");
        Assert.eq(sm.require(t2).role, Role.USER, "resolve role");

        Assert.throwsEx(AuthenticationException.class,
                () -> sm.open("carol", Role.USER), "third login over the cap is rejected");

        sm.close(t1);                                              // frees one permit
        String t3 = sm.open("carol", Role.USER);                  // now succeeds
        Assert.eq(sm.require(t3).username, "carol", "permit reused after close");

        Assert.throwsEx(AuthenticationException.class,
                () -> sm.require("bogus-token"), "unknown token rejected");
    }
}
