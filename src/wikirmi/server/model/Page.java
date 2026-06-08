package wikirmi.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import wikirmi.common.dto.*;

/**
 * A wiki page plus its own {@link ReentrantReadWriteLock}. All mutable fields are read
 * under the read lock and mutated under the write lock by {@link wikirmi.server.store.WikiStore}.
 * The per-page lock gives fine-grained concurrency: many readers at once, exclusive writers,
 * and no global server lock.
 */
public final class Page {
    private final String title;
    private String content;
    private long version;
    private String lastEditor;
    private long lastModified;
    private EditLock editLock;                       // null == free to edit
    private final List<Dto.Revision> history = new ArrayList<>();
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

    public Page(String title, String content, long version, String lastEditor, long lastModified) {
        this.title = title;
        this.content = content;
        this.version = version;
        this.lastEditor = lastEditor;
        this.lastModified = lastModified;
    }

    public ReentrantReadWriteLock lock() { return rw; }

    public String title() { return title; }
    public String content() { return content; }
    public void setContent(String c) { this.content = c; }
    public long version() { return version; }
    public void setVersion(long v) { this.version = v; }
    public String lastEditor() { return lastEditor; }
    public void setLastEditor(String e) { this.lastEditor = e; }
    public long lastModified() { return lastModified; }
    public void setLastModified(long t) { this.lastModified = t; }
    public EditLock editLock() { return editLock; }
    public void setEditLock(EditLock l) { this.editLock = l; }
    public List<Dto.Revision> history() { return history; }
}
