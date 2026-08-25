package de.tum.cit.aet.artemis.globalsearch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Registers the operational tuning records for the global search module.
 * <p>
 * The connection settings live with the client in {@link WeaviateClientConfiguration}; everything that tunes how
 * Artemis keeps the index in step with the database is bound here. All of it is gated on Weaviate being enabled,
 * matching the services that read it.
 */
@Lazy
@Configuration
@Conditional(WeaviateEnabled.class)
@EnableConfigurationProperties({ WeaviateOutboxProperties.class, WeaviateMigrationProperties.class })
public class WeaviatePropertiesConfiguration {
}
