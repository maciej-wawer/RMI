package wikirmi.common.exceptions;

/** Thrown on save when the page was modified since the client loaded it (optimistic check). */
public class VersionConflictException extends WikiException {
    private static final long serialVersionUID = 1L;
    private final long currentVersion;

    public VersionConflictException(long currentVersion) {
        super("Konflikt wersji — strona została w międzyczasie zmieniona (aktualna wersja: "
                + currentVersion + ").");
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() { return currentVersion; }
}
