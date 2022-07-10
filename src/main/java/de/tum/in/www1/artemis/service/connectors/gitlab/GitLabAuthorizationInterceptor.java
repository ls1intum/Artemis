package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.io.IOException;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("gitlab")
@Component
public class GitLabAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    public static final String GITLAB_AUTHORIZATION_HEADER_NAME = "Private-Token";

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(GITLAB_AUTHORIZATION_HEADER_NAME, gitlabPrivateToken);
        return execution.execute(request, body);
    }
}
