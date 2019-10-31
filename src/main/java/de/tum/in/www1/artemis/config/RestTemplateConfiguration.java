package de.tum.in.www1.artemis.config;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabHeaderAuthorizationInterceptor;

/**
 * For now only provides a basic {@link org.springframework.web.client.RestTemplate RestTemplate} bean. Can be extended
 * to further customize how requests to other REST APIs are handled
 */
@Configuration
public class RestTemplateConfiguration {

    @Bean
    @Profile("gitlab")
    @Autowired
    public RestTemplate restTemplate(GitLabHeaderAuthorizationInterceptor gitlabInterceptor) {
        final var restTemplate = new RestTemplate();
        var interceptors = restTemplate.getInterceptors();
        if (interceptors.isEmpty()) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(gitlabInterceptor);
        restTemplate.setInterceptors(interceptors);

        return restTemplate;
    }

    @Bean
    @Profile("!gitlab")
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
