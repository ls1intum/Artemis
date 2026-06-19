package de.tum.cit.aet.artemis.videosource.service;

import org.springframework.http.HttpStatusCode;

/**
 * Typed exception wrapping errors from the gocast (TUM Live) service-account integration API.
 * <p>
 * Carries the HTTP status associated with the failure, so callers can distinguish between e.g. a
 * {@code 404} (unknown on-behalf-of user — fall back to the public path), a {@code 403} (binding revoked
 * — mark binding as REVOKED) and a {@code 5xx} (server error). For HTTP-status errors this is the real
 * upstream status; for transport/I-O failures (connection refused, DNS, timeout) the connector reports a
 * synthetic {@code 503 SERVICE_UNAVAILABLE}.
 */
public class GocastIntegrationException extends RuntimeException {

    private final HttpStatusCode upstreamStatus;

    /**
     * Constructs a new {@link GocastIntegrationException} with a message and the upstream HTTP status.
     *
     * @param message        human-readable description of the error
     * @param upstreamStatus the HTTP status code returned by gocast (may be used by callers to decide on fallback behaviour)
     */
    public GocastIntegrationException(String message, HttpStatusCode upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * Constructs a new {@link GocastIntegrationException} wrapping an underlying cause.
     *
     * @param message        human-readable description of the error
     * @param upstreamStatus the upstream HTTP status (real status for HTTP errors, or a synthetic status such as
     *                           {@code 503}/{@code 502} for transport or empty-body failures)
     * @param cause          the underlying exception (e.g. {@code RestClientResponseException} or a transport
     *                           {@code RestClientException})
     */
    public GocastIntegrationException(String message, HttpStatusCode upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    /**
     * Returns the HTTP status code that the upstream gocast server returned, allowing callers to
     * implement typed fallback behaviour (e.g. treat {@code 404} as "unknown user, fall back to public path").
     *
     * @return upstream HTTP status code
     */
    public HttpStatusCode getUpstreamStatus() {
        return upstreamStatus;
    }
}
