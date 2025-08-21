package de.tum.cit.aet.artemis.programming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for external CI connector integration.
 * This configuration is active when the "external-ci" profile is enabled,
 * which replaces the direct Jenkins integration with a microservice approach.
 */
@Configuration
@Profile("external-ci")
public class ExternalCIConfiguration {

    /**
     * RestTemplate for communicating with the external CI connector microservice.
     *
     * @return configured RestTemplate
     */
    @Bean("externalCIRestTemplate")
    public RestTemplate externalCIRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // TODO: Add any necessary interceptors, error handlers, or timeouts
        return restTemplate;
    }
}
