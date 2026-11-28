package de.tum.cit.aet.artemis.sharing.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.config.RestClientConfiguration;
import io.micrometer.observation.ObservationRegistry;

@Profile(PROFILE_CORE)
@Conditional(SharingEnabled.class)
@Configuration
@Lazy
public class SharingRestClientConfiguration {

    /**
     * The client for the Sharing Platform, with short timeouts. It is a client of its own so that a test can mock the platform without
     * affecting the other services.
     * <p>
     * It records no observations. The observation of a request is tagged with its URL, and the URLs of the Sharing Platform carry the
     * shared API key as a query parameter and basket tokens in the path, so the metrics of this client would publish both.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client with short timeouts for the Sharing Platform
     */
    @Bean
    public RestClient sharingRestClient(RestClient.Builder restClientBuilder) {
        return RestClientConfiguration.withShortTimeout(restClientBuilder).observationRegistry(ObservationRegistry.NOOP).build();
    }
}
