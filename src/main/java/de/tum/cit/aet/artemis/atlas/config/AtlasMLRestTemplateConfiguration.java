package de.tum.cit.aet.artemis.atlas.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
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
@Conditional(AtlasEnabled.class)
@Configuration
@Lazy
public class AtlasMLRestTemplateConfiguration {

    private static final int ATLASML_CONNECTION_TIMEOUT = 30 * 1000; // 30 seconds

    private static final int ATLASML_READ_TIMEOUT = 60 * 1000; // 60 seconds

    private static final int ATLASML_SHORT_CONNECTION_TIMEOUT = 5 * 1000; // 5 seconds

    private static final int ATLASML_SHORT_READ_TIMEOUT = 10 * 1000; // 10 seconds

    @Value("${atlas.atlasml.base-url:http://atlasml.aet.cit.tum.de/}")
    private String atlasmlBaseUrl;

    @Value("${atlas.atlasml.auth-token:}")
    private String atlasmlAuthToken;

    /**
     * Creates a RestTemplate for AtlasML with standard timeouts.
     *
     * @return a RestTemplate configured for AtlasML communication
     */
    @Bean
    @Lazy
    public RestTemplate atlasmlRestTemplate() {
        return createRestTemplate(ATLASML_READ_TIMEOUT, ATLASML_CONNECTION_TIMEOUT);
    }

    /**
     * Creates a RestTemplate for AtlasML with short timeouts for health checks.
     *
     * @return a RestTemplate with short timeouts for AtlasML health checks
     */
    @Bean
    @Lazy
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

    public String getAtlasmlAuthToken() {
        return atlasmlAuthToken;
    }
}
