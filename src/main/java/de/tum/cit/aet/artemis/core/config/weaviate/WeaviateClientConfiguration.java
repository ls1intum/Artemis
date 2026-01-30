package de.tum.cit.aet.artemis.core.config.weaviate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Configuration for Weaviate client
 */
@Lazy
@Configuration
@Conditional(WeaviateEnabled.class)
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
public class WeaviateClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WeaviateClientConfiguration.class);

    private final WeaviateConfigurationProperties weaviateProperties;

    public WeaviateClientConfiguration(WeaviateConfigurationProperties weaviateProperties) {
        this.weaviateProperties = weaviateProperties;
    }

    private void validateConfiguration() {
        if (weaviateProperties.httpHost() == null || weaviateProperties.httpHost().isBlank()) {
            throw new IllegalStateException("artemis.weaviate.http-host must be configured when Weaviate is enabled");
        }
        if (weaviateProperties.httpPort() == 0) {
            throw new IllegalStateException("artemis.weaviate.http-port must be configured when Weaviate is enabled");
        }
        if (weaviateProperties.grpcPort() == 0) {
            throw new IllegalStateException("artemis.weaviate.grpc-port must be configured when Weaviate is enabled");
        }
        if (weaviateProperties.scheme() == null || weaviateProperties.scheme().isBlank()) {
            throw new IllegalStateException("artemis.weaviate.scheme must be configured when Weaviate is enabled");
        }
    }

    /**
     * Creates and configures a Weaviate client bean.
     *
     * @return the configured WeaviateClient instance
     * @throws IllegalStateException       if required configuration properties are missing
     * @throws WeaviateConnectionException if the connection to Weaviate fails
     */
    @Bean
    public WeaviateClient weaviateClient() {
        validateConfiguration();

        try {
            WeaviateClient client;
            if (weaviateProperties.secure()) {
                // Custom connection for HTTPS/secure connections
                client = WeaviateClient.connectToCustom(config -> config.scheme(weaviateProperties.scheme()).httpHost(weaviateProperties.httpHost())
                        .httpPort(weaviateProperties.httpPort()).grpcHost(weaviateProperties.httpHost()).grpcPort(weaviateProperties.grpcPort()));
            }
            else {
                // Local connection for non-secure connections
                client = WeaviateClient
                        .connectToLocal(config -> config.host(weaviateProperties.httpHost()).port(weaviateProperties.httpPort()).grpcPort(weaviateProperties.grpcPort()));
            }

            log.info("Connected to Weaviate at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort());
            return client;
        }
        catch (Exception exception) {
            log.error("Failed to connect to Weaviate at {}://{}:{} (gRPC port: {})", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                    weaviateProperties.grpcPort(), exception);
            throw new WeaviateConnectionException("Failed to connect to Weaviate", exception, weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                    weaviateProperties.grpcPort(), weaviateProperties.secure());
        }
    }
}
