package de.tum.cit.aet.artemis.core.config.weaviate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.weaviate.client6.WeaviateClient;
import io.weaviate.client6.options.WeaviateLocalOptions;

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
        log.info("Connecting to Weaviate at {}:{} (gRPC: {})", properties.getHost(), properties.getPort(), properties.getGrpcPort());

        WeaviateLocalOptions.Builder optionsBuilder = WeaviateLocalOptions.builder().host(properties.getHost()).port(properties.getPort()).grpcPort(properties.getGrpcPort());

        if (properties.isSecure()) {
            optionsBuilder.secure(true);
        }

        WeaviateClient client = WeaviateClient.connectToLocal(optionsBuilder.build());

        log.info("Successfully connected to Weaviate");
        return client;
    }
}
