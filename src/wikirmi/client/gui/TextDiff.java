package wikirmi.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free line diff (LCS) used to compare two page revisions. */
public final class TextDiff {
    private TextDiff() {}

    public enum Type { COMMON, ADDED, REMOVED }

    public static final class Segment {
        public final Type type;
        public final String text;
        public Segment(Type type, String text) { this.type = type; this.text = text; }
    }

    /** Diff old vs new text line-by-line via a longest-common-subsequence table. */
    public static List<Segment> diff(String oldText, String newText) {
        String[] a = splitLines(oldText), b = splitLines(newText);
        int n = a.length, m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);

        List<Segment> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) { out.add(new Segment(Type.COMMON, a[i])); i++; j++; }
            else if (lcs[i + 1][j] >= lcs[i][j + 1]) { out.add(new Segment(Type.REMOVED, a[i])); i++; }
            else { out.add(new Segment(Type.ADDED, b[j])); j++; }
        }
        while (i < n) out.add(new Segment(Type.REMOVED, a[i++]));
        while (j < m) out.add(new Segment(Type.ADDED, b[j++]));
        return out;
    }

    /** Render a diff as colored HTML (green added / red removed). */
    public static String toHtml(String oldText, String newText) {
        StringBuilder sb = new StringBuilder("<html><body style=\"font-family:monospace; font-size:12px;\">");
        for (Segment seg : diff(oldText, newText)) {
            String esc = escape(seg.text);
            switch (seg.type) {
                case ADDED:   sb.append("<div style=\"background:#dcffe0;\">+&nbsp;").append(esc).append("</div>"); break;
                case REMOVED: sb.append("<div style=\"background:#ffe0e0;\">-&nbsp;").append(esc).append("</div>"); break;
                default:      sb.append("<div>&nbsp;&nbsp;").append(esc).append("</div>");
            }
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String[] splitLines(String s) { return (s == null ? "" : s).split("\n", -1); }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace(" ", "&nbsp;");
    }
}
