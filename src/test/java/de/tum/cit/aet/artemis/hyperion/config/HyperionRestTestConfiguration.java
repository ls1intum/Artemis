package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for Hyperion REST services.
 * Provides mock beans for testing without external dependencies.
 */
@Configuration
@Profile(PROFILE_HYPERION)
public class HyperionRestTestConfiguration {

    @Bean
    @Primary
    public HyperionRestConfigurationProperties testHyperionProperties() {
        HyperionRestConfigurationProperties properties = new HyperionRestConfigurationProperties();
        properties.setUrl("http://localhost:8080");
        properties.setApiKey("test-api-key");
        return properties;
    }
}
