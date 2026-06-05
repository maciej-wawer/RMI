package wikirmi.server.store;

/**
 * Injectable time source. Production uses {@link #SYSTEM}; tests use {@link Manual}
 * to make lease-expiry behaviour deterministic without real sleeping.
 */
public interface Clock {
    long now();

    Clock SYSTEM = System::currentTimeMillis;

    /** Manually-advanced clock for deterministic tests. */
    final class Manual implements Clock {
        private long t;
        public Manual(long start) { this.t = start; }
        @Override public long now() { return t; }
        public void advance(long deltaMs) { t += deltaMs; }
    }
}
