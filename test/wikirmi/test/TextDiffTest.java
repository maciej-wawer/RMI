package wikirmi.test;

import java.util.List;

import wikirmi.client.gui.TextDiff;
import wikirmi.client.gui.TextDiff.Segment;
import wikirmi.client.gui.TextDiff.Type;

/** Verifies the LCS line diff classifies common/added/removed lines correctly. */
public class TextDiffTest {
    public static void run() {
        List<Segment> d = TextDiff.diff("a\nb\nc", "a\nx\nc");
        int common = 0, added = 0, removed = 0;
        boolean removedB = false, addedX = false;
        for (Segment s : d) {
            if (s.type == Type.COMMON) common++;
            if (s.type == Type.ADDED) { added++; if (s.text.equals("x")) addedX = true; }
            if (s.type == Type.REMOVED) { removed++; if (s.text.equals("b")) removedB = true; }
        }
        Assert.eq(common, 2, "two common lines (a, c)");
        Assert.eq(removed, 1, "one removed line");
        Assert.eq(added, 1, "one added line");
        Assert.isTrue(removedB, "line 'b' detected as removed");
        Assert.isTrue(addedX, "line 'x' detected as added");

        for (Segment s : TextDiff.diff("a\nb", "a\nb"))
            Assert.isTrue(s.type == Type.COMMON, "identical text yields only common lines");
    }
}
