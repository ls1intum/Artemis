package de.tum.cit.aet.artemis.programming.service.sharing;

import java.io.Serial;

/**
 * Sharing Exception during import or export to sharing platform.
 */
public class SharingException extends Exception {

    @Serial
    private static final long serialVersionUID = 8782312342L;

    public SharingException(String message) {
        super(message);
    }

    public SharingException(String message, Throwable cause) {
        super(message, cause);
    }

    public static SharingException withEndpoint(String endpoint, Throwable cause) {
        return new SharingException("Failed to connect to sharing platform at " + endpoint, cause);
    }
}
