package wikirmi.common.exceptions;

/**
 * Base class for all domain (business-logic) errors. Serializable so it travels
 * across RMI to the client, where it is turned into a user-facing message.
 */
public class WikiException extends Exception {
    private static final long serialVersionUID = 1L;
    public WikiException(String message) { super(message); }
}
