package de.tum.cit.aet.artemis.core.config.weaviate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Weaviate integration.
 * Uses a Java record for immutable configuration.
 *
 * @param enabled  whether Weaviate integration is enabled
 * @param host     the Weaviate server host
 * @param port     the Weaviate HTTP port
 * @param grpcPort the Weaviate gRPC port
 * @param scheme   the HTTP scheme (http/https) - determines secure connection type
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String host, int port, int grpcPort, String scheme) {

    private static final String DEFAULT_HOST = "localhost";

    private static final int DEFAULT_HTTP_PORT = 8080;

    private static final int DEFAULT_GRPC_PORT = 50051;

    private static final String HTTP_SCHEME = "http";

    private static final String HTTPS_SCHEME = "https";

    /**
     * Creates a new WeaviateConfigurationProperties with default values.
     */
    public WeaviateConfigurationProperties {
        if (host == null || host.isBlank()) {
            host = DEFAULT_HOST;
        }
        if (port == 0) {
            port = DEFAULT_HTTP_PORT;
        }
        if (grpcPort == 0) {
            grpcPort = DEFAULT_GRPC_PORT;
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = HTTP_SCHEME;
        }
    }

    /**
     * Creates a new instance with default values.
     */
    public WeaviateConfigurationProperties() {
        this(false, DEFAULT_HOST, DEFAULT_HTTP_PORT, DEFAULT_GRPC_PORT, HTTP_SCHEME);
    }

    /**
     * Returns whether secure connections should be used based on the scheme.
     *
     * @return true if scheme is "https", false otherwise
     */
    public boolean secure() {
        return HTTPS_SCHEME.equals(scheme);
    }
}
