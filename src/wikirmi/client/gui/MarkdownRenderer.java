package wikirmi.client.gui;

import java.util.regex.Pattern;

/**
 * Tiny dependency-free Markdown→HTML renderer for the page viewer. Supports a small subset:
 * {@code #/##/###} headers, {@code **bold**}, {@code *italic*}, {@code - } lists, blank-line breaks,
 * and {@code [[Tytuł]]} wiki links rendered as clickable {@code <a href="wiki:Tytuł">}.
 */
public final class MarkdownRenderer {
    private MarkdownRenderer() {}

    public static final String LINK_SCHEME = "wiki:";
    private static final Pattern BOLD   = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern LINK   = Pattern.compile("\\[\\[(.+?)\\]\\]");

    public static String toHtml(String markdown) {
        String[] lines = (markdown == null ? "" : markdown).split("\n", -1);
        StringBuilder sb = new StringBuilder("<html><body style=\"font-family:sans-serif; font-size:12px;\">");
        boolean inList = false;
        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("### ")) {
                inList = closeList(sb, inList);
                sb.append("<h3>").append(inline(escape(line.substring(4)))).append("</h3>");
            } else if (line.startsWith("## ")) {
                inList = closeList(sb, inList);
                sb.append("<h2>").append(inline(escape(line.substring(3)))).append("</h2>");
            } else if (line.startsWith("# ")) {
                inList = closeList(sb, inList);
                sb.append("<h1>").append(inline(escape(line.substring(2)))).append("</h1>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) { sb.append("<ul>"); inList = true; }
                sb.append("<li>").append(inline(escape(line.substring(2)))).append("</li>");
            } else if (line.trim().isEmpty()) {
                inList = closeList(sb, inList);
                sb.append("<br>");
            } else {
                inList = closeList(sb, inList);
                sb.append(inline(escape(line))).append("<br>");
            }
        }
        closeList(sb, inList);
        sb.append("</body></html>");
        return sb.toString();
    }

    /** Returns the page title encoded in a {@code wiki:} hyperlink, or null if not a wiki link. */
    public static String pageFromLink(String href) {
        if (href == null || !href.startsWith(LINK_SCHEME)) return null;
        return href.substring(LINK_SCHEME.length());
    }

    private static boolean closeList(StringBuilder sb, boolean inList) {
        if (inList) sb.append("</ul>");
        return false;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String inline(String s) {
        s = BOLD.matcher(s).replaceAll("<b>$1</b>");
        s = ITALIC.matcher(s).replaceAll("<i>$1</i>");
        s = LINK.matcher(s).replaceAll("<a href=\"" + LINK_SCHEME + "$1\">$1</a>");
        return s;
    }
}
