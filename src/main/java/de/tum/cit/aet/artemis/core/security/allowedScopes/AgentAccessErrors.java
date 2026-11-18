package de.tum.cit.aet.artemis.core.security.allowedScopes;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

/**
 * Answers a request that agent access rejects before an endpoint runs, with the status and a problem document.
 * <p>
 * The status is written directly instead of with {@code sendError}: a servlet container answers {@code sendError} with an error page
 * dispatch, which is authenticated anew and has lost the token, so a client would see 401 whatever status was meant.
 */
public final class AgentAccessErrors {

    private AgentAccessErrors() {
    }

    /**
     * Writes the answer.
     *
     * @param response the response to write to
     * @param status   the HTTP status
     * @param message  what the client should know
     * @throws IOException if the response cannot be written
     */
    public static void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(JsonObjectMapper.get().writeValueAsString(Map.of("status", status.value(), "title", status.getReasonPhrase(), "message", message)));
    }
}
