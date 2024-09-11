package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.ArrayList;

import jakarta.validation.constraints.NotNull;

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

import de.tum.cit.aet.artemis.config.auth.AthenaAuthorizationInterceptor;
import de.tum.cit.aet.artemis.config.auth.PyrisAuthorizationInterceptor;
import de.tum.cit.aet.artemis.service.connectors.gitlab.GitLabAuthorizationInterceptor;
import de.tum.cit.aet.artemis.service.connectors.jenkins.JenkinsAuthorizationInterceptor;

/**
 * For now only provides a basic {@link org.springframework.web.client.RestTemplate RestTemplate} bean. Can be extended
 * to further customize how requests to other REST APIs are handled
 */
@Profile(PROFILE_CORE)
@Configuration
public class RestTemplateConfiguration {

    private static final int SHORT_CONNECTION_TIMEOUT = 10 * 1000;

    private static final int SHORT_READ_TIMEOUT = 10 * 1000;

    private static final int VERY_SHORT_CONNECTION_TIMEOUT = 1000;

    private static final int VERY_SHORT_READ_TIMEOUT = 1000;

    @Bean
    @Profile("gitlab | gitlabci")
    @Autowired // ok
    public RestTemplate gitlabRestTemplate(GitLabAuthorizationInterceptor gitlabInterceptor) {
        return initializeRestTemplateWithInterceptors(gitlabInterceptor, createRestTemplate());
    }

    @Bean
    @Profile("jenkins")
    @Autowired // ok
    public RestTemplate jenkinsRestTemplate(JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return initializeRestTemplateWithInterceptors(jenkinsInterceptor, createRestTemplate());
    }

    @Bean
    @Profile("athena")
    public RestTemplate athenaRestTemplate(AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(athenaAuthorizationInterceptor, createRestTemplate());
    }

    @Bean
    @Profile("apollon")
    public RestTemplate apollonRestTemplate() {
        return createRestTemplate();
    }

    /**
     * Creates a RestTemplate that can be used to communicate with Aeolus
     *
     * @return a RestTemplate with short timeouts
     */
    @Bean
    @Profile("aeolus | localci")
    public RestTemplate aeolusRestTemplate() {
        return createRestTemplate();
    }

    @Bean
    @Profile("iris")
    public RestTemplate pyrisRestTemplate(PyrisAuthorizationInterceptor pyrisAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(pyrisAuthorizationInterceptor, createRestTemplate());
    }

    // Note: for certain requests, e.g. health(), we would like to have shorter timeouts, therefore we need additional rest templates, because
    // it is recommended to keep the timeout settings constant per rest template

    @Bean
    @Profile("gitlab | gitlabci")
    @Autowired // ok
    public RestTemplate shortTimeoutGitlabRestTemplate(GitLabAuthorizationInterceptor gitlabInterceptor) {
        return initializeRestTemplateWithInterceptors(gitlabInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile("jenkins")
    @Autowired // ok
    public RestTemplate shortTimeoutJenkinsRestTemplate(JenkinsAuthorizationInterceptor jenkinsInterceptor) {
        return initializeRestTemplateWithInterceptors(jenkinsInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile("athena")
    public RestTemplate shortTimeoutAthenaRestTemplate(AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(athenaAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    @Bean
    @Profile("apollon")
    public RestTemplate shortTimeoutApollonRestTemplate() {
        return createShortTimeoutRestTemplate();
    }

    // Note: for certain requests, e.g. the Athena submission selection, we would like to have even shorter timeouts.
    // Therefore, we need additional rest templates. It is recommended to keep the timeout settings constant per rest template.

    @Bean
    @Profile("athena")
    public RestTemplate veryShortTimeoutAthenaRestTemplate(AthenaAuthorizationInterceptor athenaAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(athenaAuthorizationInterceptor, createVeryShortTimeoutRestTemplate());
    }

    @Bean
    @Profile("iris")
    public RestTemplate shortTimeoutPyrisRestTemplate(PyrisAuthorizationInterceptor pyrisAuthorizationInterceptor) {
        return initializeRestTemplateWithInterceptors(pyrisAuthorizationInterceptor, createShortTimeoutRestTemplate());
    }

    /**
     * Creates a RestTemplate that can be used to communicate with Aeolus
     *
     * @return a RestTemplate with short timeouts
     */
    @Bean
    @Profile("aeolus | localci")
    public RestTemplate shortTimeoutAeolusRestTemplate() {
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

    private RestTemplate createRestTemplate() {
        return new RestTemplate();
    }

    private RestTemplate createShortTimeoutRestTemplate() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(SHORT_READ_TIMEOUT);
        requestFactory.setConnectTimeout(SHORT_CONNECTION_TIMEOUT);
        return new RestTemplate(requestFactory);
    }

    private RestTemplate createVeryShortTimeoutRestTemplate() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(VERY_SHORT_READ_TIMEOUT);
        requestFactory.setConnectTimeout(VERY_SHORT_CONNECTION_TIMEOUT);
        return new RestTemplate(requestFactory);
    }
}
