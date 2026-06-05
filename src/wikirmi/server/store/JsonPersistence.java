package wikirmi.server.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import wikirmi.common.Role;
import wikirmi.common.dto.RevisionDTO;
import wikirmi.common.exceptions.ValidationException;
import wikirmi.server.auth.PasswordHasher;
import wikirmi.server.model.Page;
import wikirmi.server.model.User;

/**
 * Persists the {@link WikiStore} to a human-readable JSON file. Saving is crash-safe: it writes
 * to a temp file and atomically renames it over the target, so an interrupted save never corrupts
 * the existing data. Edit-locks are runtime-only and intentionally not persisted.
 */
public final class JsonPersistence {
    private static final Object SAVE_LOCK = new Object();

    private JsonPersistence() {}

    public static void save(WikiStore store, Path path) {
        Map<String, Object> root = new LinkedHashMap<>();

        List<Object> usersJson = new ArrayList<>();
        for (User u : store.allUsers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.username());
            m.put("salt", u.salt());
            m.put("hash", u.hash());
            m.put("role", u.role().name());
            usersJson.add(m);
        }
        root.put("users", usersJson);

        List<Object> pagesJson = new ArrayList<>();
        for (Page p : store.allPages()) {
            p.lock().readLock().lock();                 // brief read lock per page for a consistent snapshot
            try {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", p.title());
                m.put("content", p.content());
                m.put("version", p.version());
                m.put("lastEditor", p.lastEditor());
                m.put("lastModified", p.lastModified());
                List<Object> hist = new ArrayList<>();
                for (RevisionDTO r : p.history()) {
                    Map<String, Object> rm = new LinkedHashMap<>();
                    rm.put("index", (long) r.getIndex());
                    rm.put("editor", r.getEditor());
                    rm.put("timestamp", r.getTimestamp());
                    rm.put("content", r.getContent());
                    hist.add(rm);
                }
                m.put("history", hist);
                pagesJson.add(m);
            } finally {
                p.lock().readLock().unlock();
            }
        }
        root.put("pages", pagesJson);

        String json = Json.write(root);
        synchronized (SAVE_LOCK) {
            try {
                Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
                Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException notAtomic) {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Nie udało się zapisać danych do " + path + ": " + e.getMessage(), e);
            }
        }
    }

    public static void load(WikiStore store, Path path) {
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Map<?, ?> root = (Map<?, ?>) Json.parse(text);

            List<?> usersJson = (List<?>) root.get("users");
            if (usersJson != null) {
                for (Object o : usersJson) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    store.addUser(new User(
                            (String) m.get("username"), (String) m.get("salt"),
                            (String) m.get("hash"), Role.valueOf((String) m.get("role"))));
                }
            }

            List<?> pagesJson = (List<?>) root.get("pages");
            if (pagesJson != null) {
                for (Object o : pagesJson) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Page p = new Page((String) m.get("title"), (String) m.get("content"),
                            asLong(m.get("version")), (String) m.get("lastEditor"), asLong(m.get("lastModified")));
                    List<?> hist = (List<?>) m.get("history");
                    if (hist != null) {
                        for (Object ho : hist) {
                            Map<?, ?> hm = (Map<?, ?>) ho;
                            p.history().add(new RevisionDTO((int) asLong(hm.get("index")),
                                    (String) hm.get("editor"), asLong(hm.get("timestamp")), (String) hm.get("content")));
                        }
                    }
                    store.putPage(p);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Nie udało się wczytać danych z " + path + ": " + e.getMessage(), e);
        }
    }

    /** Loads the file if present; otherwise seeds a default admin account and a sample page, then saves. */
    public static void loadOrSeed(WikiStore store, Path path, String adminUser, String adminPassword) {
        if (Files.exists(path)) {
            load(store, path);
            return;
        }
        String salt = PasswordHasher.newSalt();
        store.addUser(new User(adminUser, salt, PasswordHasher.hash(adminPassword, salt), Role.ADMIN));
        try {
            store.createPage("Strona główna",
                    "Witaj w WikiRMI!\n\nTo jest przykładowa strona startowa utworzona przez administratora.\n"
                            + "Zaloguj się i użyj przycisku \"Edytuj\", aby zmodyfikować jej treść.",
                    adminUser);
        } catch (ValidationException ignored) {
            // seed content is always valid; ignore
        }
        save(store, path);
    }

    private static long asLong(Object o) { return ((Number) o).longValue(); }
}
