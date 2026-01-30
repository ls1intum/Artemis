package de.tum.cit.aet.artemis.core.config.weaviate;

import static de.tum.cit.aet.artemis.core.config.ConfigurationValidator.HTTPS_SCHEME;
import static de.tum.cit.aet.artemis.core.config.ConfigurationValidator.HTTP_SCHEME;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for Weaviate integration.
 * Uses a Java record for immutable configuration.
 *
 * @param enabled  whether Weaviate integration is enabled
 * @param httpHost the Weaviate server HTTP host
 * @param httpPort the Weaviate HTTP port
 * @param grpcPort the Weaviate gRPC port
 * @param scheme   the HTTP scheme (http/https) - determines secure connection type
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String httpHost, int httpPort, int grpcPort, String scheme) {

    private static final String DEFAULT_HTTP_HOST = "localhost";

    private static final int DEFAULT_HTTP_PORT = 8001;

    private static final int DEFAULT_GRPC_PORT = 50051;

    /**
     * Creates a new WeaviateConfigurationProperties with default values.
     */
    @ConstructorBinding
    public WeaviateConfigurationProperties {
        if (httpHost == null || httpHost.isBlank()) {
            httpHost = DEFAULT_HTTP_HOST;
        }
        if (httpPort == 0) {
            httpPort = DEFAULT_HTTP_PORT;
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
        this(false, DEFAULT_HTTP_HOST, DEFAULT_HTTP_PORT, DEFAULT_GRPC_PORT, HTTP_SCHEME);
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
