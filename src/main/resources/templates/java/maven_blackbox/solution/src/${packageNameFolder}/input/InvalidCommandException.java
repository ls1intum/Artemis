package ${packageName}.input;

import java.io.Serial;

/**
 * Error type to be used to signal that the user entered an invalid command.
 */
public class InvalidCommandException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Builds a new exception that signals that an invalid command was entered.
     *
     * @param message A message that should be understandable for the user.
     */
    public InvalidCommandException(final String message) {
        super(message);
    }
}
