package de.tum.cit.aet.artemis.core.config.weaviate;

import static de.tum.cit.aet.artemis.core.config.ConfigurationValidator.HTTPS_SCHEME;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Weaviate integration.
 * Uses a Java record for immutable configuration.
 * <p>
 * When Weaviate is enabled, all properties must be explicitly configured.
 * Validation is performed in {@link WeaviateClientConfiguration} and {@link de.tum.cit.aet.artemis.core.config.ConfigurationValidator#validateWeaviateConfiguration}.
 *
 * @param enabled          whether Weaviate integration is enabled
 * @param httpHost         the Weaviate server HTTP host
 * @param httpPort         the Weaviate HTTP port
 * @param grpcPort         the Weaviate gRPC port
 * @param scheme           the HTTP scheme (http/https) - determines secure connection type
 * @param collectionPrefix prefix prepended to all Weaviate collection names to avoid naming conflicts (e.g. with Pyris collections)
 */
@ConfigurationProperties(prefix = "artemis.weaviate")
public record WeaviateConfigurationProperties(boolean enabled, String httpHost, @DefaultValue(DEFAULT_HTTP_PORT) int httpPort, @DefaultValue(DEFAULT_GRPC_PORT) int grpcPort,
        String scheme, @DefaultValue(DEFAULT_COLLECTION_PREFIX) String collectionPrefix) {

    public static final String DEFAULT_HTTP_PORT = "8001";

    public static final String DEFAULT_GRPC_PORT = "50051";

    public static final String DEFAULT_COLLECTION_PREFIX = "";

    /**
     * Returns whether secure connections should be used based on the scheme.
     *
     * @return true if scheme is "https", false otherwise
     */
    public boolean secure() {
        return HTTPS_SCHEME.equals(scheme);
    }
}
