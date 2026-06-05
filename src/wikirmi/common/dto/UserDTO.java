package wikirmi.common.dto;

import java.io.Serializable;
import wikirmi.common.Role;

/** A user account as seen over the wire. Never carries the password hash or salt. */
public final class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private final Role role;

    public UserDTO(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() { return username; }
    public Role getRole() { return role; }
}
