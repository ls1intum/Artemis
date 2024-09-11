package de.tum.cit.aet.artemis.athena.config;

import java.io.IOException;

import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@Profile("athena")
public class AthenaAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.athena.secret}")
    private String secret;

    @Value("${server.url}")
    private String artemisServerUrl;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, secret);
        request.getHeaders().set("X-Server-URL", artemisServerUrl);
        return execution.execute(request, body);
    }
}
