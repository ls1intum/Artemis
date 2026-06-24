package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration for the gocast (TUM Live) service-account integration.
 * <p>
 * Exposes a {@link RestClient} bean pre-configured with the gocast API base URL.
 * This bean is only created when {@link GocastEnabled} is active, i.e. when both
 * {@code artemis.tum-live.api-base-url} and {@code artemis.tum-live.service-account-token} are non-blank.
 * <p>
 * The service-account bearer token and {@code X-On-Behalf-Of} header are <em>not</em> added as
 * global interceptors here; they are set per-request in {@code GocastConnectorService} because the token
 * is the same for every call but the on-behalf-of user varies per request.
 */
@Configuration
@Lazy
@Conditional(GocastEnabled.class)
public class GocastConfiguration {

    /**
     * Creates a {@link RestClient} pre-configured with the gocast API base URL.
     * <p>
     * This bean is only registered when the gocast service-account integration is fully configured
     * (see {@link GocastEnabled}).
     *
     * @param builder the auto-configured {@link RestClient.Builder} provided by Spring Boot
     * @param baseUrl the gocast REST API base URL from {@code artemis.tum-live.api-base-url}
     * @return a {@link RestClient} targeting the gocast integration endpoints
     */
    @Bean
    public RestClient gocastIntegrationRestClient(RestClient.Builder builder, @Value("${artemis.tum-live.api-base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
