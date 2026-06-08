package wikirmi.test;

import java.io.*;

import wikirmi.common.Role;
import wikirmi.common.dto.*;

/** Confirms DTOs survive Java serialization (i.e. they can cross the RMI wire). */
public class SerializationTest {

    static <T> T roundTrip(T obj) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(bytes)) { os.writeObject(obj); }
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked") T result = (T) is.readObject();
            return result;
        }
    }

    public static void run() throws Exception {
        Dto.LockInfo lock = new Dto.LockInfo("alice", 100L, 130L, 30L);
        Dto.Page page = new Dto.Page("Home", "witaj świecie", 3, "alice", 111L, lock);
        Dto.Page rp = roundTrip(page);
        Assert.eq(rp.getTitle(), "Home", "title round-trips");
        Assert.eq(rp.getContent(), "witaj świecie", "UTF-8 content round-trips");
        Assert.eq(rp.getVersion(), 3, "version round-trips");
        Assert.eq(rp.getLock().getHolder(), "alice", "nested lock DTO round-trips");

        Dto.Session session = roundTrip(new Dto.Session("tok-123", new Dto.User("alice", Role.ADMIN)));
        Assert.eq(session.getToken(), "tok-123", "token round-trips");
        Assert.eq(session.getUser().getRole(), Role.ADMIN, "enum role round-trips");
    }
}
