package de.tum.cit.aet.artemis.programming.service.jenkins;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;

    public JenkinsAuthorizationInterceptor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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
        final var headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        final var entity = new HttpEntity<>(headers);

        try {
            final var response = restTemplate.exchange(jenkinsServerUri.toString() + "/crumbIssuer/api/json", HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
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
