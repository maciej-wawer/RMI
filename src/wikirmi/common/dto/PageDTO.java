package wikirmi.common.dto;

import java.io.Serializable;

/** Full page content as seen over the wire. {@code lock} is null when the page is free. */
public final class PageDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String title;
    private final String content;
    private final long version;
    private final String lastEditor;
    private final long lastModified;
    private final LockInfoDTO lock;

    public PageDTO(String title, String content, long version, String lastEditor,
                   long lastModified, LockInfoDTO lock) {
        this.title = title;
        this.content = content;
        this.version = version;
        this.lastEditor = lastEditor;
        this.lastModified = lastModified;
        this.lock = lock;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getVersion() { return version; }
    public String getLastEditor() { return lastEditor; }
    public long getLastModified() { return lastModified; }
    public LockInfoDTO getLock() { return lock; }
}
