package wikirmi.common.exceptions;

/**
 * Base class for all domain (business-logic) errors. Serializable so it travels
 * across RMI to the client, where it is turned into a user-facing message.
 */
public class WikiException extends Exception {
    private static final long serialVersionUID = 1L;
    public WikiException(String message) { super(message); }

    /** Komunikat oznaczający nieważną/wygasłą sesję — wspólny dla serwera i klienta. */
    public static final String SESSION_INVALID = "Sesja wygasła lub jest nieprawidłowa. Zaloguj się ponownie.";

    /** Czy ten wyjątek (lub jego przyczyna) oznacza nieważną sesję? Klient wtedy się wylogowuje. */
    public static boolean isSessionInvalid(Throwable t) {
        for (Throwable r = t; r != null; r = r.getCause())
            if (r instanceof WikiException && SESSION_INVALID.equals(r.getMessage())) return true;
        return false;
    }
}
