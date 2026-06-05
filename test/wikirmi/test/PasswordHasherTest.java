package wikirmi.test;

import wikirmi.server.auth.PasswordHasher;

/** Verifies salted hashing: correct passwords verify, wrong ones don't, salts are unique. */
public class PasswordHasherTest {
    public static void run() {
        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("tajneHasło", salt);
        Assert.isTrue(PasswordHasher.verify("tajneHasło", salt, hash), "correct password verifies");
        Assert.isTrue(!PasswordHasher.verify("złeHasło", salt, hash), "wrong password rejected");
        Assert.isTrue(!salt.equals(PasswordHasher.newSalt()), "each salt is unique");
        Assert.isTrue(!hash.equals(PasswordHasher.hash("tajneHasło", PasswordHasher.newSalt())),
                "same password with a different salt yields a different hash");
    }
}
