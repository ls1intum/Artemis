package de.tum.cit.aet.artemis.account.security.passkey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import de.tum.cit.aet.artemis.core.exception.SessionOutdatedException;

/**
 * Writes the answer for a passkey registration request whose session was established before the account's credentials last
 * changed.
 * <p>
 * The passkey registration runs in servlet filters, where an exception cannot reach the controller advice that turns
 * {@link SessionOutdatedException} into a response elsewhere. The body has the same shape, so the client treats both alike.
 */
final class SessionOutdatedResponse {

    private SessionOutdatedResponse() {
    }

    /**
     * Writes a 401 response that tells the client the session has to be re-established.
     *
     * @param response the response of the rejected request
     * @throws IOException if the body cannot be written
     */
    static void write(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":401,\"errorKey\":\"" + SessionOutdatedException.ERROR_KEY + "\",\"message\":\"error." + SessionOutdatedException.ERROR_KEY + "\"}");
    }
}
