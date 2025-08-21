package de.tum.cit.aet.artemis.jenkins.connector.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate beans used for Jenkins API communication.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${jenkins.username:admin}")
    private String jenkinsUsername;

    @Value("${jenkins.password:admin}")
    private String jenkinsPassword;

    /**
     * RestTemplate configured for Jenkins API calls with authentication.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication(jenkinsUsername, jenkinsPassword)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .build();
    }

    /**
     * RestTemplate with shorter timeout for health checks.
     */
    @Bean
    public RestTemplate shortTimeoutRestTemplate(RestTemplateBuilder builder) {
        return builder
                .basicAuthentication(jenkinsUsername, jenkinsPassword)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .build();
    }
}