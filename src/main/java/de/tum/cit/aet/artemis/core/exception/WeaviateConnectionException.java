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

    private final String host;

    private final int port;

    private final int grpcPort;

    private final boolean secure;

    public WeaviateConnectionException(String message, Throwable cause, String host, int port, int grpcPort, boolean secure) {
        super(message, cause);
        this.host = host;
        this.port = port;
        this.grpcPort = grpcPort;
        this.secure = secure;
    }

    /**
     * Gets the host where the connection failure occurred.
     *
     * @return the host name or IP address
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port where the connection failure occurred.
     *
     * @return the HTTP port number
     */
    public int getPort() {
        return port;
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
