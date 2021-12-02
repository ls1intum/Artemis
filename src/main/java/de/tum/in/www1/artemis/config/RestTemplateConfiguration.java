package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.ArrayList;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.config.auth.AtheneAuthorizationInterceptor;
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

    private static final int SHORT_CONNECTION_TIMEOUT = 10 * 1000;

    private static final int SHORT_READ_TIMEOUT = 10 * 1000;

    @Bean
    @Profile(SPRING_PROFILE_GITLAB)
    @Autowired // ok
    public RestTemplate gitlabRestTemplate(GitLabAuthorizationInterceptor gitlabInterceptor) {
        return initializeRestTemplateWithInterceptors(gitlabInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_JENKINS)
    @Autowired // ok
    public RestTemplate jenkinsRestTemplate(JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return initializeRestTemplateWithInterceptors(jenkinsInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_JIRA)
    @Autowired // ok
    public RestTemplate jiraRestTemplate(JiraAuthorizationInterceptor jiraAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(jiraAuthorizationInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_BITBUCKET)
    public RestTemplate bitbucketRestTemplate(BitbucketAuthorizationInterceptor bitbucketAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bitbucketAuthorizationInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_BAMBOO)
    public RestTemplate bambooRestTemplate(BambooAuthorizationInterceptor bambooAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bambooAuthorizationInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_ATHENE)
    public RestTemplate atheneRestTemplate(AtheneAuthorizationInterceptor atheneAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(atheneAuthorizationInterceptor, createRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_APOLLON)
    public RestTemplate apollonRestTemplate() {
        return createRestTemplate();
    }

    // Note: for certain requests, e.g. health(), we would like to have shorter timeouts, therefore we need additional rest templates, because
    // it is recommended to keep the timeout settings constant per rest template

    @Bean
    @Profile(SPRING_PROFILE_GITLAB)
    @Autowired // ok
    public RestTemplate shortTimeoutGitlabRestTemplate(GitLabAuthorizationInterceptor gitlabInterceptor) {
        return initializeRestTemplateWithInterceptors(gitlabInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_JENKINS)
    @Autowired // ok
    public RestTemplate shortTimeoutJenkinsRestTemplate(JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return initializeRestTemplateWithInterceptors(jenkinsInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_JIRA)
    @Autowired // ok
    public RestTemplate shortTimeoutJiraRestTemplate(JiraAuthorizationInterceptor jiraAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(jiraAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_BITBUCKET)
    public RestTemplate shortTimeoutBitbucketRestTemplate(BitbucketAuthorizationInterceptor bitbucketAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bitbucketAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_BAMBOO)
    public RestTemplate shortTimeoutBambooRestTemplate(BambooAuthorizationInterceptor bambooAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(bambooAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_ATHENE)
    public RestTemplate shortTimeoutAtheneRestTemplate(AtheneAuthorizationInterceptor atheneAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(atheneAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile(SPRING_PROFILE_APOLLON)
    public RestTemplate shortTimeoutApollonRestTemplate() {
        return createShortTimeoutRestTemplate();
    }

    @NotNull
    private RestTemplate initializeRestTemplateWithInterceptors(ClientHttpRequestInterceptor interceptor, RestTemplate restTemplate) {
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

    private RestTemplate createShortTimeoutRestTemplate() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(SHORT_READ_TIMEOUT);
        requestFactory.setConnectTimeout(SHORT_CONNECTION_TIMEOUT);
        return new RestTemplate(requestFactory);
    }

    private RestTemplate createRestTemplate() {
        return new RestTemplate();
    }
}
