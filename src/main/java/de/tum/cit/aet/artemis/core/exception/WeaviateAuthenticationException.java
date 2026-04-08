package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.WeaviateAuthenticationFailureAnalyzer;

/**
 * Exception thrown when authentication to Weaviate fails during application startup (HTTP 401).
 * This exception is caught by {@link WeaviateAuthenticationFailureAnalyzer} to provide
 * helpful error messages about configuring API key authentication.
 */
public class WeaviateAuthenticationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String httpHost;

    private final int httpPort;

    private final boolean secure;

    public WeaviateAuthenticationException(String message, Throwable cause, String httpHost, int httpPort, boolean secure) {
        super(message, cause);
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.secure = secure;
    }

    /**
     * Gets the HTTP host where the authentication failure occurred.
     *
     * @return the HTTP host name or IP address
     */
    public String getHttpHost() {
        return httpHost;
    }

    /**
     * Gets the HTTP port where the authentication failure occurred.
     *
     * @return the HTTP port number
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Gets whether the connection was configured for secure mode.
     *
     * @return true if secure mode was enabled, false otherwise
     */
    public boolean isSecure() {
        return secure;
    }
}
