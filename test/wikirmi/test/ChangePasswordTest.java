package wikirmi.test;

import wikirmi.common.Role;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.store.Clock;
import wikirmi.server.store.WikiStore;

/** Changing a password replaces the stored salt+hash; old stops working, role is preserved. */
public class ChangePasswordTest {
    public static void run() throws Exception {
        WikiStore s = new WikiStore(30000, Clock.SYSTEM);
        String salt = PasswordHasher.newSalt();
        s.createUser("alice", salt, PasswordHasher.hash("stareHaslo", salt), Role.USER);
        Assert.isTrue(PasswordHasher.verify("stareHaslo", s.getUser("alice").salt(), s.getUser("alice").hash()),
                "old password works initially");

        String ns = PasswordHasher.newSalt();
        s.updatePassword("alice", ns, PasswordHasher.hash("noweHaslo", ns));

        Assert.isTrue(PasswordHasher.verify("noweHaslo", s.getUser("alice").salt(), s.getUser("alice").hash()),
                "new password works after change");
        Assert.isTrue(!PasswordHasher.verify("stareHaslo", s.getUser("alice").salt(), s.getUser("alice").hash()),
                "old password no longer works");
        Assert.eq(s.getUser("alice").role(), Role.USER, "role preserved across password change");
    }
}
