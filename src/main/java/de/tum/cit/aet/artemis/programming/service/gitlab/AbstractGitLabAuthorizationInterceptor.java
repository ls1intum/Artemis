package de.tum.cit.aet.artemis.programming.service.gitlab;

import java.io.IOException;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

public abstract class AbstractGitLabAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private static final String GITLAB_AUTHORIZATION_HEADER_NAME = "Private-Token";

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(GITLAB_AUTHORIZATION_HEADER_NAME, gitlabPrivateToken);
        return execution.execute(request, body);
    }
}
