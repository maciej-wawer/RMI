package wikirmi.test;

import java.nio.file.Files;
import java.nio.file.Path;

import wikirmi.common.Role;
import wikirmi.common.dto.*;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.store.Clock;
import wikirmi.server.store.JsonPersistence;
import wikirmi.server.store.WikiStore;

/** Simulates a server restart: save a store to JSON, reload into a fresh store, verify state survived. */
public class RestartPersistenceTest {
    public static void run() throws Exception {
        Path f = Files.createTempFile("wiki-test", ".json");
        Files.deleteIfExists(f);                                   // start from no file
        try {
            WikiStore s1 = new WikiStore(30000, Clock.SYSTEM);
            String salt = PasswordHasher.newSalt();
            s1.createUser("alice", salt, PasswordHasher.hash("pw", salt), Role.ADMIN);
            s1.createPage("Home", "hello", "alice");
            s1.acquireEditLock("Home", "t", "alice");
            Dto.Page cur = s1.getPage("Home");
            s1.savePage("Home", "t", "alice", "edited", cur.getVersion());
            JsonPersistence.save(s1, f);

            WikiStore s2 = new WikiStore(30000, Clock.SYSTEM);      // "restarted" server
            JsonPersistence.load(s2, f);

            Dto.Page p = s2.getPage("Home");
            Assert.eq(p.getContent(), "edited", "page content survived restart");
            Assert.eq(p.getVersion(), 1, "page version survived restart");
            Assert.eq(s2.getHistory("Home").size(), 1, "edit history survived restart");
            Assert.isTrue(s2.getUser("alice") != null, "user account survived restart");
            Assert.isTrue(PasswordHasher.verify("pw", s2.getUser("alice").salt(), s2.getUser("alice").hash()),
                    "password hash survived restart");
            Assert.isTrue(p.getLock() == null, "edit-lock is NOT persisted (runtime-only state)");
        } finally {
            Files.deleteIfExists(f);
        }
    }
}
