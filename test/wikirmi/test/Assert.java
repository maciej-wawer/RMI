package wikirmi.test;

/** Minimal assertion helpers for the dependency-free test harness. */
public final class Assert {
    private Assert() {}

    public static void isTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void eq(Object actual, Object expected, String msg) {
        boolean equal = (actual == null) ? expected == null : actual.equals(expected);
        if (!equal) throw new AssertionError(msg + " | expected=" + expected + " actual=" + actual);
    }

    public static void eq(long actual, long expected, String msg) {
        if (actual != expected) throw new AssertionError(msg + " | expected=" + expected + " actual=" + actual);
    }

    public interface ThrowingRunnable { void run() throws Exception; }

    /** Asserts that running {@code r} throws an exception assignable to {@code type}; returns it. */
    public static <T extends Throwable> T throwsEx(Class<T> type, ThrowingRunnable r, String msg) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError(msg + " | threw wrong type: " + t);
        }
        throw new AssertionError(msg + " | nothing thrown (expected " + type.getSimpleName() + ")");
    }
}
