package wikirmi.server;

/** Tunable server constants (documented in the report). */
public final class ServerConfig {
    private ServerConfig() {}

    public static final int    REGISTRY_PORT = wikirmi.common.Protocol.DEFAULT_REGISTRY_PORT;
    public static final String SERVICE_NAME  = wikirmi.common.Protocol.SERVICE_NAME;
    public static final int    MAX_CLIENTS   = 50;          // Semaphore permits
    public static final long   LEASE_MS      = 30_000;      // edit-lock lease
    public static final long   REAP_MS       = 5_000;       // reaper period
    public static final String DATA_FILE     = "wiki.json";

    public static final String DEFAULT_ADMIN          = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";
}
