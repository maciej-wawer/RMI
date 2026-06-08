package wikirmi.common.dto;

import java.io.Serializable;

import wikirmi.common.Role;

/**
 * Obiekty transferu danych (DTO) przesyłane przez RMI — wszystkie w jednym pliku,
 * jako klasy zagnieżdżone. Każda jest Serializable, więc może podróżować przez sieć.
 * Używamy: Dto.Page, Dto.PageSummary, Dto.Revision, Dto.User, Dto.LockInfo, Dto.Session.
 */
public final class Dto {
    private Dto() {}

    /** Konto użytkownika widziane przez sieć (nigdy nie zawiera hasła ani skrótu). */
    public static final class User implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String username;
        private final Role role;
        public User(String username, Role role) { this.username = username; this.role = role; }
        public String getUsername() { return username; }
        public Role getRole() { return role; }
    }

    /** Jeden wpis w historii zmian strony. */
    public static final class Revision implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int index;
        private final String editor;
        private final long timestamp;
        private final String content;
        public Revision(int index, String editor, long timestamp, String content) {
            this.index = index; this.editor = editor; this.timestamp = timestamp; this.content = content;
        }
        public int getIndex() { return index; }
        public String getEditor() { return editor; }
        public long getTimestamp() { return timestamp; }
        public String getContent() { return content; }
    }

    /** Informacja o aktywnej blokadzie edycji (null = strona wolna). */
    public static final class LockInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String holder;
        private final long acquiredAt;
        private final long expiresAt;
        private final long remainingMillis;
        public LockInfo(String holder, long acquiredAt, long expiresAt, long remainingMillis) {
            this.holder = holder; this.acquiredAt = acquiredAt; this.expiresAt = expiresAt; this.remainingMillis = remainingMillis;
        }
        public String getHolder() { return holder; }
        public long getAcquiredAt() { return acquiredAt; }
        public long getExpiresAt() { return expiresAt; }
        public long getRemainingMillis() { return remainingMillis; }
    }

    /** Lekki wiersz strony na listy i wyniki wyszukiwania (bez treści). */
    public static final class PageSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String title;
        private final long version;
        private final String lockedBy;     // null = nikt nie edytuje
        private final long lastModified;
        public PageSummary(String title, long version, String lockedBy, long lastModified) {
            this.title = title; this.version = version; this.lockedBy = lockedBy; this.lastModified = lastModified;
        }
        public String getTitle() { return title; }
        public long getVersion() { return version; }
        public String getLockedBy() { return lockedBy; }
        public long getLastModified() { return lastModified; }
    }

    /** Pełna treść strony. {@code lock} jest null, gdy strona jest wolna. */
    public static final class Page implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String title;
        private final String content;
        private final long version;
        private final String lastEditor;
        private final long lastModified;
        private final LockInfo lock;
        public Page(String title, String content, long version, String lastEditor, long lastModified, LockInfo lock) {
            this.title = title; this.content = content; this.version = version;
            this.lastEditor = lastEditor; this.lastModified = lastModified; this.lock = lock;
        }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public long getVersion() { return version; }
        public String getLastEditor() { return lastEditor; }
        public long getLastModified() { return lastModified; }
        public LockInfo getLock() { return lock; }
    }

    /** Zwracane przy logowaniu: token sesji + publiczne dane użytkownika. */
    public static final class Session implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String token;
        private final User user;
        public Session(String token, User user) { this.token = token; this.user = user; }
        public String getToken() { return token; }
        public User getUser() { return user; }
    }
}
