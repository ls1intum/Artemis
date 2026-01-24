package de.tum.cit.aet.artemis.core.config.weaviate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Configuration class for the Weaviate client bean.
 * This creates and configures the Weaviate client based on the application properties.
 */
@Configuration
@ConditionalOnProperty(name = "artemis.weaviate.enabled", havingValue = "true")
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
public class WeaviateClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WeaviateClientConfiguration.class);

    private final WeaviateConfigurationProperties properties;

    public WeaviateClientConfiguration(WeaviateConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the Weaviate client bean.
     *
     * @return the configured WeaviateClient
     */
    @Bean
    public WeaviateClient weaviateClient() {
        String scheme = properties.isSecure() ? "https" : "http";
        log.info("Connecting to Weaviate at {}://{}:{} (gRPC: {})", scheme, properties.getHost(), properties.getPort(), properties.getGrpcPort());

        WeaviateClient client;
        if (properties.isSecure()) {
            // Use custom config for secure connections
            client = WeaviateClient.connectToCustom(config -> config.scheme(scheme).httpHost(properties.getHost()).httpPort(properties.getPort()).grpcHost(properties.getHost())
                    .grpcPort(properties.getGrpcPort()));
        }
        else {
            // Use local config for non-secure connections
            client = WeaviateClient.connectToLocal(config -> config.host(properties.getHost()).port(properties.getPort()).grpcPort(properties.getGrpcPort()));
        }

        log.info("Successfully connected to Weaviate");
        return client;
    }
}
