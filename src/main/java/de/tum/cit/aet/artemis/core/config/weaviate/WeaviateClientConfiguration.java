package de.tum.cit.aet.artemis.core.config.weaviate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tum.cit.aet.artemis.core.exception.WeaviateConnectionException;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Configuration for Weaviate client
 */
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
            if (weaviateProperties.isSecure()) {
                // Custom connection for HTTPS/secure connections
                client = WeaviateClient.connectToCustom(config -> config.scheme(weaviateProperties.getScheme()).httpHost(weaviateProperties.getHost())
                        .httpPort(weaviateProperties.getPort()).grpcHost(weaviateProperties.getHost()).grpcPort(weaviateProperties.getGrpcPort()));
            }
            else {
                // Local connection for non-secure connections
                client = WeaviateClient
                        .connectToLocal(config -> config.host(weaviateProperties.getHost()).port(weaviateProperties.getPort()).grpcPort(weaviateProperties.getGrpcPort()));
            }

            log.info("Connected to Weaviate at {}://{}:{}", weaviateProperties.getScheme(), weaviateProperties.getHost(), weaviateProperties.getPort());
            return client;
        }
        catch (Exception exception) {
            log.error("Failed to connect to Weaviate at {}://{}:{} (gRPC port: {})", weaviateProperties.getScheme(), weaviateProperties.getHost(), weaviateProperties.getPort(),
                    weaviateProperties.getGrpcPort(), exception);
            throw new WeaviateConnectionException("Failed to connect to Weaviate", exception, weaviateProperties.getHost(), weaviateProperties.getPort(),
                    weaviateProperties.getGrpcPort(), weaviateProperties.isSecure());
        }
    }
}
