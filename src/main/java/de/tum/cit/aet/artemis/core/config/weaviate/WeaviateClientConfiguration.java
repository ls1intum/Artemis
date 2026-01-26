package de.tum.cit.aet.artemis.core.config.weaviate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Configuration for Weaviate client
 */
@Lazy
@Configuration
@ConditionalOnProperty(prefix = "artemis.weaviate", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WeaviateConfigurationProperties.class)
public class WeaviateClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WeaviateClientConfiguration.class);

    private final WeaviateConfigurationProperties weaviateProperties;

    public WeaviateClientConfiguration(WeaviateConfigurationProperties weaviateProperties) {
        this.weaviateProperties = weaviateProperties;
    }

    /**
     * Creates and configures a Weaviate client bean.
     *
     * @return the configured WeaviateClient instance
     * @throws WeaviateConnectionException if the connection to Weaviate fails
     */
    @Bean
    public WeaviateClient weaviateClient() {
        try {
            WeaviateClient client;
            if (weaviateProperties.secure()) {
                // Custom connection for HTTPS/secure connections
                client = WeaviateClient.connectToCustom(config -> config.scheme(weaviateProperties.scheme()).httpHost(weaviateProperties.host()).httpPort(weaviateProperties.port())
                        .grpcHost(weaviateProperties.host()).grpcPort(weaviateProperties.grpcPort()));
            }
            else {
                // Local connection for non-secure connections
                client = WeaviateClient.connectToLocal(config -> config.host(weaviateProperties.host()).port(weaviateProperties.port()).grpcPort(weaviateProperties.grpcPort()));
            }

            log.info("Connected to Weaviate at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.host(), weaviateProperties.port());
            return client;
        }
        catch (Exception exception) {
            log.error("Failed to connect to Weaviate at {}://{}:{} (gRPC port: {})", weaviateProperties.scheme(), weaviateProperties.host(), weaviateProperties.port(),
                    weaviateProperties.grpcPort(), exception);
            throw new WeaviateConnectionException("Failed to connect to Weaviate", exception, weaviateProperties.host(), weaviateProperties.port(), weaviateProperties.grpcPort(),
                    weaviateProperties.secure());
        }
    }
}
