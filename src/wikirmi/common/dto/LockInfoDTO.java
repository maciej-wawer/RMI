package wikirmi.common.dto;

import java.io.Serializable;

/** Describes an active edit-lock on a page (null when the page is free to edit). */
public final class LockInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String holder;
    private final long acquiredAt;
    private final long expiresAt;
    private final long remainingMillis;

    public LockInfoDTO(String holder, long acquiredAt, long expiresAt, long remainingMillis) {
        this.holder = holder;
        this.acquiredAt = acquiredAt;
        this.expiresAt = expiresAt;
        this.remainingMillis = remainingMillis;
    }

    public String getHolder() { return holder; }
    public long getAcquiredAt() { return acquiredAt; }
    public long getExpiresAt() { return expiresAt; }
    public long getRemainingMillis() { return remainingMillis; }
}
