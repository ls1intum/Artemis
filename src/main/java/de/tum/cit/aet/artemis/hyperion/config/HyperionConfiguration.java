package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.hyperion.client.ApiClient;
import de.tum.cit.aet.artemis.hyperion.client.api.HealthcheckApi;
import de.tum.cit.aet.artemis.hyperion.client.api.ReviewAndRefineApi;

/**
 * Configuration for Hyperion API clients using generated OpenAPI client code.
 */
@Configuration
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionConfiguration {

    @Value("${artemis.hyperion.url}")
    private String hyperionUrl;

    @Value("${artemis.hyperion.api-key}")
    private String hyperionApiKey;

    /**
     * ApiClient configured for regular operations with standard timeout.
     * Used for consistency checks and other primary operations.
     * Authentication is handled automatically by the generated ApiClient.
     */
    @Bean
    public ApiClient hyperionApiClient(@Qualifier("hyperionRestTemplate") RestTemplate restTemplate) {
        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath(hyperionUrl);
        if (hyperionApiKey != null && !hyperionApiKey.isEmpty()) {
            apiClient.setApiKey(hyperionApiKey);
        }
        return apiClient;
    }

    /**
     * ApiClient configured for health checks with short timeout.
     * Used specifically for health monitoring to avoid long waits.
     * Authentication is handled automatically by the generated ApiClient.
     */
    @Bean
    public ApiClient hyperionHealthApiClient(@Qualifier("shortTimeoutHyperionRestTemplate") RestTemplate shortTimeoutRestTemplate) {
        ApiClient apiClient = new ApiClient(shortTimeoutRestTemplate);
        apiClient.setBasePath(hyperionUrl);
        if (hyperionApiKey != null && !hyperionApiKey.isEmpty()) {
            apiClient.setApiKey(hyperionApiKey);
        }
        return apiClient;
    }

    /**
     * HealthcheckApi for performing health checks against Hyperion service.
     * Uses the short timeout configuration for responsive health monitoring.
     */
    @Bean
    public HealthcheckApi healthcheckApi(@Qualifier("hyperionHealthApiClient") ApiClient healthApiClient) {
        return new HealthcheckApi(healthApiClient);
    }

    /**
     * ReviewAndRefineApi for consistency checks and other review operations.
     * Uses the standard timeout configuration for processing operations.
     */
    @Bean
    public ReviewAndRefineApi reviewAndRefineApi(@Qualifier("hyperionApiClient") ApiClient apiClient) {
        return new ReviewAndRefineApi(apiClient);
    }
}
