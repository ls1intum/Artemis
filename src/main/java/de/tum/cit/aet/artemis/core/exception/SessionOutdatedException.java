package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a session that was established before the account's credentials last changed tries to create a new credential.
 * <p>
 * Answered with 401 rather than 403 on purpose: the client already treats a 401 of an authenticated user as "this session is
 * no longer valid" and sends the user to sign in again, which is exactly what has to happen here.
 */
public class SessionOutdatedException extends HttpStatusException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ENTITY_NAME = "session";

    public static final String ERROR_KEY = "sessionOutdated";

    public SessionOutdatedException() {
        super(ErrorConstants.DEFAULT_TYPE, "The session was established before the credentials of the account changed. Please sign in again.", HttpStatus.UNAUTHORIZED, ENTITY_NAME,
                ERROR_KEY, getAlertParameters(ENTITY_NAME, ERROR_KEY, false));
    }
}
