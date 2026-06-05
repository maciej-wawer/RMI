package wikirmi.common.dto;

import java.io.Serializable;

/** One entry in a page's edit history. */
public final class RevisionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int index;
    private final String editor;
    private final long timestamp;
    private final String content;

    public RevisionDTO(int index, String editor, long timestamp, String content) {
        this.index = index;
        this.editor = editor;
        this.timestamp = timestamp;
        this.content = content;
    }

    public int getIndex() { return index; }
    public String getEditor() { return editor; }
    public long getTimestamp() { return timestamp; }
    public String getContent() { return content; }
}
