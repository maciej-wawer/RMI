package wikirmi.test;

import wikirmi.client.gui.MarkdownRenderer;

/** Verifies the Markdown subset and wiki-link rendering. */
public class MarkdownRendererTest {
    public static void run() {
        String html = MarkdownRenderer.toHtml(
                "# Tytuł\n**pogrubienie** i *kursywa*\n- jeden\n- dwa\nZobacz [[Strona główna]].");
        Assert.isTrue(html.contains("<h1>Tytuł</h1>"), "renders h1 header");
        Assert.isTrue(html.contains("<b>pogrubienie</b>"), "renders bold");
        Assert.isTrue(html.contains("<i>kursywa</i>"), "renders italic");
        Assert.isTrue(html.contains("<li>jeden</li>") && html.contains("<li>dwa</li>"), "renders list items");
        Assert.isTrue(html.contains("<ul>") && html.contains("</ul>"), "wraps list in <ul>");
        Assert.isTrue(html.contains("href=\"wiki:Strona główna\"") && html.contains(">Strona główna</a>"),
                "renders [[wiki link]]");

        Assert.eq(MarkdownRenderer.pageFromLink("wiki:Strona główna"), "Strona główna", "extracts page from wiki: link");
        Assert.isTrue(MarkdownRenderer.pageFromLink("http://example") == null, "ignores non-wiki links");

        Assert.isTrue(MarkdownRenderer.toHtml("a < b & c").contains("a &lt; b &amp; c"), "escapes HTML special chars");
    }
}
