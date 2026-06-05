package wikirmi.common.exceptions;

/** Bad credentials, or an invalid/expired session token. */
public class AuthenticationException extends WikiException {
    private static final long serialVersionUID = 1L;
    public AuthenticationException(String message) { super(message); }
}
