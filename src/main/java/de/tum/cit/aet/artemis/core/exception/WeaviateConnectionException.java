package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import de.tum.cit.aet.artemis.core.exception.failureAnalyzer.WeaviateConnectionFailureAnalyzer;

/**
 * Exception thrown when the connection to Weaviate fails during application startup.
 * This exception is caught by {@link WeaviateConnectionFailureAnalyzer} to provide
 * helpful error messages about verifying Weaviate availability and network configuration.
 */
public class WeaviateConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String httpHost;

    private final int httpPort;

    private final int grpcPort;

    private final boolean secure;

    public WeaviateConnectionException(String message, Throwable cause, String httpHost, int httpPort, int grpcPort, boolean secure) {
        super(message, cause);
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.grpcPort = grpcPort;
        this.secure = secure;
    }

    /**
     * Gets the HTTP host where the connection failure occurred.
     *
     * @return the HTTP host name or IP address
     */
    public String getHttpHost() {
        return httpHost;
    }

    /**
     * Gets the HTTP port where the connection failure occurred.
     *
     * @return the HTTP port number
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Gets the gRPC port where the connection failure occurred.
     *
     * @return the gRPC port number
     */
    public int getGrpcPort() {
        return grpcPort;
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
