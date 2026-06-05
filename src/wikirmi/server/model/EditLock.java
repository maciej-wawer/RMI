package wikirmi.server.model;

/**
 * The logical edit-lease held by one user while editing a page. Immutable; renewal
 * replaces it with a new instance. This is server state (not a Java monitor) so it can
 * be held across human think-time and reclaimed by the reaper daemon when it expires.
 */
public final class EditLock {
    private final String holderToken;
    private final String holderName;
    private final long acquiredAt;
    private final long expiresAt;

    public EditLock(String holderToken, String holderName, long acquiredAt, long expiresAt) {
        this.holderToken = holderToken;
        this.holderName = holderName;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(long now) { return now >= expiresAt; }
    public boolean heldBy(String token) { return holderToken.equals(token); }

    public String holderToken() { return holderToken; }
    public String holderName() { return holderName; }
    public long acquiredAt() { return acquiredAt; }
    public long expiresAt() { return expiresAt; }
}
