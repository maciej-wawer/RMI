package wikirmi.server.model;

import wikirmi.common.Role;

/** A server-side user account. The salt+hash never leave the server. */
public final class User {
    private final String username;
    private final String salt;
    private final String hash;
    private final Role role;

    public User(String username, String salt, String hash, Role role) {
        this.username = username;
        this.salt = salt;
        this.hash = hash;
        this.role = role;
    }

    public String username() { return username; }
    public String salt() { return salt; }
    public String hash() { return hash; }
    public Role role() { return role; }
}
