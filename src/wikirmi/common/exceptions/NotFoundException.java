package wikirmi.common.exceptions;

/** A referenced page or user does not exist. */
public class NotFoundException extends WikiException {
    private static final long serialVersionUID = 1L;
    public NotFoundException(String message) { super(message); }
}
