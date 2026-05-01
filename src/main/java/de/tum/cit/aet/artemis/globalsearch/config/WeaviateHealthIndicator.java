package de.tum.cit.aet.artemis.globalsearch.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Health indicator for the Weaviate vector database used by the global search feature.
 * Only registered when Weaviate is configured to be active (see {@link WeaviateEnabled}).
 */
@Lazy
@Conditional(WeaviateEnabled.class)
@Component
public class WeaviateHealthIndicator implements HealthIndicator {

    private final WeaviateClient client;

    private final WeaviateConfigurationProperties properties;

    public WeaviateHealthIndicator(WeaviateClient client, WeaviateConfigurationProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        String address = properties.scheme() + "://" + properties.httpHost() + ":" + properties.httpPort();
        try {
            if (client.isReady()) {
                return Health.up().withDetail("Address", address).build();
            }
            return Health.down().withDetail("Address", address).withDetail("ready", false).build();
        }
        catch (Exception e) {
            return Health.down(e).withDetail("Address", address).build();
        }
    }
}
