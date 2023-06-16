package de.tum.in.www1.artemis.exception;

import org.springframework.context.annotation.Profile;

/**
 * Exception related to the connected LSP functionalities
 */
@Profile("lsp")
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
