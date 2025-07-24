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
