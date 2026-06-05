package wikirmi.common.dto;

import java.io.Serializable;

/** Returned by login: the session token plus the authenticated user's public info. */
public final class SessionDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final UserDTO user;

    public SessionDTO(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public UserDTO getUser() { return user; }
}
