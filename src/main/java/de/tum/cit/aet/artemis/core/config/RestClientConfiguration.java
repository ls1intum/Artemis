package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;

/**
 * The {@link RestClient} beans for the connectors that already moved away from the deprecated
 * {@link org.springframework.web.client.RestTemplate} of {@link RestTemplateConfiguration}. The remaining connectors follow one by one.
 * <p>
 * Every client is built from the {@link RestClient.Builder} of Spring Boot, so that the message converters and the customizers of the
 * application apply, but keeps {@link SimpleClientHttpRequestFactory}, the HTTP stack of the former templates. Without it, Spring Boot
 * would pick Apache HttpClient 5, which has other connection pooling and timeout defaults.
 * <p>
 * As with the templates, the timeout belongs to the client: a call that has to fail faster gets its own bean.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class RestClientConfiguration {

    private static final int SHORT_CONNECTION_TIMEOUT = 10 * 1000;

    private static final int SHORT_READ_TIMEOUT = 10 * 1000;

    /**
     * The client for the conversion calls to Apollon, without a timeout like the template it replaces.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client for the Apollon conversion service
     */
    @Bean
    @Conditional(ApollonEnabled.class)
    public RestClient apollonRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.requestFactory(new SimpleClientHttpRequestFactory()).build();
    }

    /**
     * The client for the health check of Apollon, which must fail fast because it blocks the actuator health endpoint.
     *
     * @param restClientBuilder the builder of Spring Boot, which is a new instance for every injection
     * @return the client with short timeouts for the Apollon health check
     */
    @Bean
    @Conditional(ApollonEnabled.class)
    public RestClient shortTimeoutApollonRestClient(RestClient.Builder restClientBuilder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(SHORT_READ_TIMEOUT);
        requestFactory.setConnectTimeout(SHORT_CONNECTION_TIMEOUT);
        return restClientBuilder.requestFactory(requestFactory).build();
    }
}
