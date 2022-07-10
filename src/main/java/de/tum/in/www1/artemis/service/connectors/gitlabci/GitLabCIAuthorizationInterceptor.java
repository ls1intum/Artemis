package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.io.IOException;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabAuthorizationInterceptor;

@Profile("gitlabci")
@Component
public class GitLabCIAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add(GitLabAuthorizationInterceptor.GITLAB_AUTHORIZATION_HEADER_NAME, gitlabPrivateToken);
        return execution.execute(request, body);
    }
}
