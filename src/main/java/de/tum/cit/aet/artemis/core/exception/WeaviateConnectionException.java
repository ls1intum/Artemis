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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public boolean isSecure() {
        return secure;
    }
}
