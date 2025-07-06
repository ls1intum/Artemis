package de.tum.cit.aet.artemis.atlas.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for AtlasML RestTemplate.
 * Provides RestTemplate beans for communicating with the AtlasML microservice.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class AtlasMLRestTemplateConfiguration {

    private static final int ATLASML_CONNECTION_TIMEOUT = 30 * 1000; // 30 seconds

    private static final int ATLASML_READ_TIMEOUT = 60 * 1000; // 60 seconds

    private static final int ATLASML_SHORT_CONNECTION_TIMEOUT = 5 * 1000; // 5 seconds

    private static final int ATLASML_SHORT_READ_TIMEOUT = 10 * 1000; // 10 seconds

    // TODO: Change to the correct URL
    @Value("${artemis.atlas.atlasml.base-url:http://localhost:8000}")
    private String atlasmlBaseUrl;

    /**
     * Creates a RestTemplate for AtlasML with standard timeouts.
     *
     * @return a RestTemplate configured for AtlasML communication
     */
    @Bean
    public RestTemplate atlasmlRestTemplate() {
        return createRestTemplate(ATLASML_READ_TIMEOUT, ATLASML_CONNECTION_TIMEOUT);
    }

    /**
     * Creates a RestTemplate for AtlasML with short timeouts for health checks.
     *
     * @return a RestTemplate with short timeouts for AtlasML health checks
     */
    @Bean
    public RestTemplate shortTimeoutAtlasmlRestTemplate() {
        return createRestTemplate(ATLASML_SHORT_READ_TIMEOUT, ATLASML_SHORT_CONNECTION_TIMEOUT);
    }

    private RestTemplate createRestTemplate(int readTimeout, int connectionTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(readTimeout);
        requestFactory.setConnectTimeout(connectionTimeout);
        return new RestTemplate(requestFactory);
    }

    public String getAtlasmlBaseUrl() {
        return atlasmlBaseUrl;
    }
}
