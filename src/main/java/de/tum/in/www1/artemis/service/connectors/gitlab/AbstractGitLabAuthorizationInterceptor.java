package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public abstract class AbstractGitLabAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private static final String GITLAB_AUTHORIZATION_HEADER_NAME = "Private-Token";

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @NonNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte @NonNull [] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(GITLAB_AUTHORIZATION_HEADER_NAME, gitlabPrivateToken);
        return execution.execute(request, body);
    }
}
