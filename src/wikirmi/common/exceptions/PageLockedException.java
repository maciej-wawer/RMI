package wikirmi.common.exceptions;

/** Thrown when a client tries to edit a page currently locked by another user. */
public class PageLockedException extends WikiException {
    private static final long serialVersionUID = 1L;
    private final String holder;
    private final long remainingSeconds;

    public PageLockedException(String holder, long remainingSeconds) {
        super("Strona jest aktualnie edytowana przez użytkownika '" + holder
                + "' (pozostało ~" + remainingSeconds + " s).");
        this.holder = holder;
        this.remainingSeconds = remainingSeconds;
    }

    public String getHolder() { return holder; }
    public long getRemainingSeconds() { return remainingSeconds; }
}
