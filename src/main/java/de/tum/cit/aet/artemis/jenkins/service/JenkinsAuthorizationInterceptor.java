package de.tum.cit.aet.artemis.jenkins.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// REMOVE_IN_ARTEMIS_11 (Jenkins): Jenkins support is removed in Artemis 11, delete with the whole jenkins module
@Profile(PROFILE_JENKINS)
@Component
@Lazy
public class JenkinsAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JenkinsAuthorizationInterceptor.class);

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URI jenkinsServerUri;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final RestClient crumbRestClient;

    public JenkinsAuthorizationInterceptor(@Qualifier("jenkinsCrumbRestClient") RestClient crumbRestClient) {
        this.crumbRestClient = crumbRestClient;
    }

    @NonNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBasicAuth(username, password);
        if (useCrumb) {
            setCrumb(request.getHeaders());
        }
        return execution.execute(request, body);
    }

    private void setCrumb(final HttpHeaders headersToAuthenticate) {
        try {
            final var response = crumbRestClient.get().uri(jenkinsServerUri.toString() + "/crumbIssuer/api/json").headers(headers -> headers.setBasicAuth(username, password))
                    .retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            final var body = response.getBody();
            final var cookies = response.getHeaders().get("Set-Cookie");
            if (body == null || !body.containsKey("crumb") || cookies == null || cookies.isEmpty()) {
                log.warn("Incomplete Jenkins crumb response: body={}, cookies={}", body, cookies);
                return;
            }
            headersToAuthenticate.add("Jenkins-Crumb", String.valueOf(body.get("crumb")));
            headersToAuthenticate.add("Cookie", cookies.getFirst());
        }
        catch (RestClientException e) {
            log.error("Cannot get Jenkins crumb from crumb issuer: {}", e.getMessage());
        }
    }
}
