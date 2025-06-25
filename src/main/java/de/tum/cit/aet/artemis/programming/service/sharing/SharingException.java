package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.Serial;

import org.springframework.context.annotation.Profile;

/**
 * Sharing Exception during import or export to sharing platform.
 */
@Profile("sharing")
public class SharingException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a SharingException with the specified message.
     *
     * @param message the detail message
     */
    public SharingException(String message) {
        super(message);
    }

    /**
     * Creates a SharingException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public SharingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a SharingException for connection failures.
     *
     * @param endpoint The endpoint that failed
     * @param cause    The underlying cause
     * @return A new SharingException
     */
    public static SharingException withEndpoint(String endpoint, Throwable cause) {
        return new SharingException("Failed to connect to sharing platform at " + endpoint, cause);
    }
}
