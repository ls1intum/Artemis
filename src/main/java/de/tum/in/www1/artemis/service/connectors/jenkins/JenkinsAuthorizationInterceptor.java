package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URL;

import org.jetbrains.annotations.NotNull;
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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

@Profile("jenkins")
@Component
public class JenkinsAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsURL;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBasicAuth(username, password);
        setCrumb(request.getHeaders());

        return execution.execute(request, body);
    }

    private void setCrumb(final HttpHeaders headersToAuthenticate) {
        final var headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        final var entity = new HttpEntity<>(headers);

        final var response = new RestTemplate().exchange(jenkinsURL.toString() + "/crumbIssuer/api/json", HttpMethod.GET, entity, JsonNode.class);
        final var sessionId = response.getHeaders().get("Set-Cookie").get(0);
        headersToAuthenticate.add("Jenkins-Crumb", response.getBody().get("crumb").asText());
        headersToAuthenticate.add("Cookie", sessionId);
    }
}
