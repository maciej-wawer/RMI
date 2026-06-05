package wikirmi.common.dto;

import java.io.Serializable;

/** Lightweight page row for lists and search results (no body content). */
public final class PageSummaryDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String title;
    private final long version;
    private final String lockedBy;   // null when the page is not being edited
    private final long lastModified;

    public PageSummaryDTO(String title, long version, String lockedBy, long lastModified) {
        this.title = title;
        this.version = version;
        this.lockedBy = lockedBy;
        this.lastModified = lastModified;
    }

    public String getTitle() { return title; }
    public long getVersion() { return version; }
    public String getLockedBy() { return lockedBy; }
    public long getLastModified() { return lastModified; }
}
