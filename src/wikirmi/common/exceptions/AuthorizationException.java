package wikirmi.common.exceptions;

/** The caller is authenticated but not permitted to perform the operation. */
public class AuthorizationException extends WikiException {
    private static final long serialVersionUID = 1L;
    public AuthorizationException(String message) { super(message); }
}
