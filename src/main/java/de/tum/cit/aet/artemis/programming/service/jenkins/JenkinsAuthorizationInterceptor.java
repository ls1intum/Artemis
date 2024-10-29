package de.tum.cit.aet.artemis.programming.service.jenkins;

import java.io.IOException;
import java.net.URL;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

import com.fasterxml.jackson.databind.JsonNode;

@Profile("jenkins")
@Component
public class JenkinsAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JenkinsAuthorizationInterceptor.class);

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsURL;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private final RestTemplate restTemplate;

    public JenkinsAuthorizationInterceptor(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
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
            final var response = restTemplate.exchange(jenkinsURL.toString() + "/crumbIssuer/api/json", HttpMethod.GET, entity, JsonNode.class);
            final var sessionId = response.getHeaders().get("Set-Cookie").getFirst();
            headersToAuthenticate.add("Jenkins-Crumb", response.getBody().get("crumb").asText());
            headersToAuthenticate.add("Cookie", sessionId);
        }
        catch (RestClientException e) {
            log.error("Cannot get Jenkins crumb from crumb issuer: {}", e.getMessage());
        }
    }
}
