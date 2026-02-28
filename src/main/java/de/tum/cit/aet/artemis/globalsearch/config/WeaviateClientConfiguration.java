package de.tum.cit.aet.artemis.globalsearch.config;

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

    /**
     * Creates and configures a Weaviate client bean.
     * Configuration validation is handled by {@link de.tum.cit.aet.artemis.core.config.ConfigurationValidator#validateWeaviateConfiguration()}.
     *
     * @return the configured WeaviateClient instance
     * @throws WeaviateConnectionException if the connection to Weaviate fails
     */
    @Bean(destroyMethod = "close")
    public WeaviateClient weaviateClient() {
        try {
            WeaviateClient client;
            if (weaviateProperties.secure()) {
                // Custom connection for HTTPS/secure connections.
                // IMPORTANT: scheme() must be called first because it auto-sets both ports (443 for https, 80 for http).
                // httpPort() and grpcPort() are called after to override with the configured values.
                client = WeaviateClient.connectToCustom(config -> config.scheme(weaviateProperties.scheme()).httpHost(weaviateProperties.httpHost())
                        .httpPort(weaviateProperties.httpPort()).grpcHost(weaviateProperties.httpHost()).grpcPort(weaviateProperties.grpcPort()));
            }
            else {
                // Local connection for non-secure connections
                client = WeaviateClient
                        .connectToLocal(config -> config.host(weaviateProperties.httpHost()).port(weaviateProperties.httpPort()).grpcPort(weaviateProperties.grpcPort()));
            }

            // The constructor already verified liveness; isReady() is an additional readiness check.
            // Catch IOException separately so a transient readiness failure doesn't destroy a valid client.
            try {
                if (client.isReady()) {
                    log.info("Connected to Weaviate at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort());
                }
                else {
                    log.warn("Weaviate client created but server is not ready at {}://{}:{}", weaviateProperties.scheme(), weaviateProperties.httpHost(),
                            weaviateProperties.httpPort());
                }
            }
            catch (Exception readinessException) {
                log.warn("Could not verify Weaviate readiness at {}://{}:{}: {}", weaviateProperties.scheme(), weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                        readinessException.getMessage());
            }
            return client;
        }
        catch (Exception exception) {
            log.error("Failed to configure Weaviate client for {}://{}:{} (gRPC port: {})", weaviateProperties.scheme(), weaviateProperties.httpHost(),
                    weaviateProperties.httpPort(), weaviateProperties.grpcPort(), exception);
            throw new WeaviateConnectionException("Failed to configure Weaviate client", exception, weaviateProperties.httpHost(), weaviateProperties.httpPort(),
                    weaviateProperties.grpcPort(), weaviateProperties.secure());
        }
    }
}
