package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

/**
 * Exception thrown when Weaviate operations fail.
 */
public class WeaviateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WeaviateException(String message, Throwable cause) {
        super(message, cause);
    }

}
