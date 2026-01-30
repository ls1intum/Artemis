package de.tum.cit.aet.artemis.core.config.weaviate;

import static de.tum.cit.aet.artemis.core.config.ConfigurationValidator.HTTPS_SCHEME;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Weaviate integration.
 * Uses a Java record for immutable configuration.
 * <p>
 * When Weaviate is enabled, all properties must be explicitly configured.
 * Validation is performed in {@link WeaviateClientConfiguration} and {@link de.tum.cit.aet.artemis.core.config.ConfigurationValidator#validateWeaviateConfiguration}.
 *
 * @param enabled  whether Weaviate integration is enabled
 * @param httpHost the Weaviate server HTTP host
 * @param httpPort the Weaviate HTTP port
 * @param grpcPort the Weaviate gRPC port
 * @param scheme   the HTTP scheme (http/https) - determines secure connection type
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String httpHost, int httpPort, int grpcPort, String scheme) {

    /**
     * Returns whether secure connections should be used based on the scheme.
     *
     * @return true if scheme is "https", false otherwise
     */
    public boolean secure() {
        return HTTPS_SCHEME.equals(scheme);
    }
}
