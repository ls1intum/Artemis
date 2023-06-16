package de.tum.in.www1.artemis.exception;

/**
 * Exception related to the connected LSP functionalities
 */
public class LspException extends RuntimeException {

    /**
     * Creates a new LSP exception
     *
     * @param message The message related to the exception
     */
    public LspException(String message) {
        super(message);
    }
}
