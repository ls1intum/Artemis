package de.tum.in.www1.artemis.config;

import java.util.ArrayList;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.config.auth.JiraAuthorizationInterceptor;
import de.tum.in.www1.artemis.service.connectors.bamboo.BambooAuthorizationInterceptor;
import de.tum.in.www1.artemis.service.connectors.bitbucket.BitbucketAuthorizationInterceptor;
import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabAuthorizationInterceptor;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsAuthorizationInterceptor;

/**
 * For now only provides a basic {@link org.springframework.web.client.RestTemplate RestTemplate} bean. Can be extended
 * to further customize how requests to other REST APIs are handled
 */
@Configuration
public class RestTemplateConfiguration {

    private static final int CONNECTION_TIMEOUT = 20 * 1000;

    private static final int READ_TIMEOUT = 20 * 1000;

    @Bean
    @Profile("gitlab")
    @Autowired
    public RestTemplate gitlabRestTemplate(GitLabAuthorizationInterceptor gitlabInterceptor) {
        return initializeRestTemplateWithInterceptors(gitlabInterceptor);
    }

    @Bean
    @Profile("jenkins")
    @Autowired
    public RestTemplate jenkinsRestTemplate(JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return initializeRestTemplateWithInterceptors(jenkinsInterceptor);
    }

    @Bean
    @Profile("jira")
    @Autowired
    public RestTemplate jiraRestTemplate(JiraAuthorizationInterceptor jiraAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(jiraAuthorizationInterceptor);
    }

    @Bean
    @Profile("bitbucket")
    public RestTemplate bitbucketRestTemplate(BitbucketAuthorizationInterceptor bitbucketAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bitbucketAuthorizationInterceptor);
    }

    @Bean
    @Profile("bamboo")
    public RestTemplate bambooRestTemplate(BambooAuthorizationInterceptor bambooAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bambooAuthorizationInterceptor);
    }

    @NotNull
    private RestTemplate initializeRestTemplateWithInterceptors(ClientHttpRequestInterceptor interceptor) {
        final var restTemplate = createRestTemplate();
        var interceptors = restTemplate.getInterceptors();
        if (interceptors.isEmpty()) {
            interceptors = new ArrayList<>();
        }
        interceptors.add(interceptor);
        restTemplate.setInterceptors(interceptors);

        // we do not want to use MappingJackson2XmlHttpMessageConverter here because it would lead to problems with the tests
        HttpMessageConverter<?> messageConverterToRemove = null;
        for (HttpMessageConverter<?> messageConverter : restTemplate.getMessageConverters()) {
            if (messageConverter instanceof MappingJackson2XmlHttpMessageConverter) {
                messageConverterToRemove = messageConverter;
            }
        }
        if (messageConverterToRemove != null) {
            restTemplate.getMessageConverters().remove(messageConverterToRemove);
        }
        return restTemplate;
    }

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return createRestTemplate();
    }

    private RestTemplate createRestTemplate() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);
        requestFactory.setConnectTimeout(CONNECTION_TIMEOUT);
        return new RestTemplate(requestFactory);
    }
}
