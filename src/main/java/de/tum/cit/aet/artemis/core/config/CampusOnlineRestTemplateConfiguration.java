package de.tum.cit.aet.artemis.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for CAMPUSOnline RestTemplate.
 * Provides a dedicated RestTemplate bean for communicating with the CAMPUSOnline API,
 * isolated from the application's shared RestTemplate to avoid mock interference in tests.
 */
@Conditional(CampusOnlineEnabled.class)
@Configuration
@Lazy
public class CampusOnlineRestTemplateConfiguration {

    private static final int CONNECTION_TIMEOUT = 10_000; // 10 seconds

    private static final int READ_TIMEOUT = 30_000; // 30 seconds

    @Bean
    @Lazy
    public RestTemplate campusOnlineRestTemplate() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECTION_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(requestFactory);
    }
}
