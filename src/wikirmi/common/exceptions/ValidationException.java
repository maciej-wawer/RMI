package wikirmi.common.exceptions;

/** Invalid user input (empty/oversized fields, duplicates, illegal arguments). */
public class ValidationException extends WikiException {
    private static final long serialVersionUID = 1L;
    public ValidationException(String message) { super(message); }
}
