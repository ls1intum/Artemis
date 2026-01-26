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

    /**
     * Creates a new WeaviateConfigurationProperties with default values and validation.
     */
    public WeaviateConfigurationProperties {
        // Apply defaults
        if (host == null || host.isBlank()) {
            host = "localhost";
        }
        if (port == 0) {
            port = 8080;
        }
        if (grpcPort == 0) {
            grpcPort = 50051;
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = secure ? "https" : "http";
        }

        // Validate scheme/secure consistency
        if (secure && "http".equals(scheme)) {
            throw new IllegalArgumentException("Configuration inconsistency: secure=true but scheme=http. Use scheme=https for secure connections.");
        }
        if (!secure && "https".equals(scheme)) {
            throw new IllegalArgumentException("Configuration inconsistency: secure=false but scheme=https. Use scheme=http for non-secure connections or set secure=true.");
        }
    }

    /**
     * Creates a new instance with default values.
     */
    public WeaviateConfigurationProperties() {
        this(false, "localhost", 8080, 50051, false, "http");
    }
}
