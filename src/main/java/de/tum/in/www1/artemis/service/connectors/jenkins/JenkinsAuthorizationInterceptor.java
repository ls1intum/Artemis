package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("jenkins")
@Component
public class JenkinsAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.jenkins.user}")
    private String username;

    @Value("${artemis.jenkins.password}")
    private String password;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBasicAuth(username, password);

        return execution.execute(request, body);
    }
}
