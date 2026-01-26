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
 * @param secure   whether to use secure connections (HTTPS/secure gRPC)
 * @param scheme   the HTTP scheme (http/https) - must be consistent with secure flag
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String host, int port, int grpcPort, boolean secure, String scheme) {

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
            scheme = secure ? HTTPS_SCHEME : HTTP_SCHEME;
        }
    }

    /**
     * Creates a new instance with default values.
     */
    public WeaviateConfigurationProperties() {
        this(false, DEFAULT_HOST, DEFAULT_HTTP_PORT, DEFAULT_GRPC_PORT, false, HTTP_SCHEME);
    }
}
