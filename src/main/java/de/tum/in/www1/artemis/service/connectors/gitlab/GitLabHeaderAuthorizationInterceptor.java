package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("gitlab")
@Component
public class GitLabHeaderAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.version-control.secret}")
    private String GITLAB_PRIVATE_TOKEN;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().add("Private-Token", GITLAB_PRIVATE_TOKEN);

        return execution.execute(request, body);
    }
}
