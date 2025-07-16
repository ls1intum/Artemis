package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * REST client configuration for Hyperion services using RestClient.
 */
@Configuration
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionRestClientConfiguration {

    private final HyperionRestConfigurationProperties hyperionProperties;

    public HyperionRestClientConfiguration(HyperionRestConfigurationProperties hyperionProperties) {
        this.hyperionProperties = hyperionProperties;
    }

    /**
     * Creates the primary RestClient for Hyperion API calls.
     *
     * @param authInterceptor the authorization interceptor for API key authentication
     * @return configured RestClient instance
     */
    @Bean
    @Qualifier("hyperionRestClient")
    public RestClient hyperionRestClient(HyperionAuthorizationInterceptor authInterceptor) {
        return RestClient.builder().baseUrl(hyperionProperties.getUrl())
                .requestFactory(createRequestFactory(hyperionProperties.getConnectionTimeout(), hyperionProperties.getReadTimeout())).requestInterceptor(authInterceptor).build();
    }

    /**
     * Creates a short-timeout RestClient for health checks and quick operations.
     * Uses reduced timeouts to fail fast for availability checks.
     *
     * @param authInterceptor the authorization interceptor for API key authentication
     * @return configured RestClient instance with short timeouts
     */
    @Bean
    @Qualifier("shortTimeoutHyperionRestClient")
    public RestClient shortTimeoutHyperionRestClient(HyperionAuthorizationInterceptor authInterceptor) {
        return RestClient.builder().baseUrl(hyperionProperties.getUrl()).requestFactory(createRequestFactory(Duration.ofSeconds(5),  // Short connection timeout for health checks
                Duration.ofSeconds(10)  // Short read timeout for health checks
        )).requestInterceptor(authInterceptor).build();
    }

    /**
     * Creates a request factory with custom timeout settings using JDK HttpClient.
     *
     * @param connectionTimeout the connection timeout duration
     * @param readTimeout       the read timeout duration
     * @return configured JdkClientHttpRequestFactory
     */
    private JdkClientHttpRequestFactory createRequestFactory(Duration connectionTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(readTimeout);
        // Note: JDK HttpClient handles connection timeout via HttpClient.Builder if needed
        return factory;
    }
}
