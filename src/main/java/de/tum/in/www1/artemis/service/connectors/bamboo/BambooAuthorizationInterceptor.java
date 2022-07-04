package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.io.IOException;
import java.util.Collections;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("bamboo")
@Component
public class BambooAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Value("${artemis.continuous-integration.password}")
    private String bambooPassword;

    @Value("${artemis.continuous-integration.token}")
    private String bambooToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        if (request.getHeaders().getAccept().isEmpty()) {
            request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        }
        if (request.getHeaders().getContentType() == null) {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }

        request.getHeaders().set("X-Atlassian-Token", "no-check");

        // certain Bamboo requests do not support token based authentication, we have to use basic auth then or we need to use cookie authentication
        String uri = request.getURI().toString();
        if (uri.contains(".action") || uri.contains("/artifact/")) {
            request.getHeaders().setBasicAuth(bambooUser, bambooPassword);
        }
        else {
            // for all other requests, we use the bamboo token because the authentication is faster and also possible in case JIRA is not available
            request.getHeaders().setBearerAuth(bambooToken);
        }

        return execution.execute(request, body);
    }
}
