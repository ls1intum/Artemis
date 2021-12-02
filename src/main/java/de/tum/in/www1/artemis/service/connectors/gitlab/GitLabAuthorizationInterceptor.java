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

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_GITLAB;

@Profile(SPRING_PROFILE_GITLAB)
@Component
public class GitLabAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add("Private-Token", gitlabPrivateToken);
        return execution.execute(request, body);
    }
}
