package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * For now only provides a basic {@link org.springframework.web.client.RestTemplate RestTemplate} bean. Can be extended
 * to further customize how requests to other REST APIs are handled
 */
@Configuration
public class RestTemplateConfiguration {

    @Bean
    @Profile("bitbucket")
    public RestTemplate bitbucketRestTemplate() {
        // TODO: authenticate here
        return new RestTemplate();
    }

    @Bean
    @Profile("bamboo")
    public RestTemplate bambooRestTemplate() {
        // TODO: authenticate here
        return new RestTemplate();
    }

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
