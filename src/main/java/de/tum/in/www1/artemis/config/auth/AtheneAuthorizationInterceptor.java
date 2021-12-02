package de.tum.in.www1.artemis.config.auth;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_ATHENE;

import java.io.IOException;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@Profile(SPRING_PROFILE_ATHENE)
public class AtheneAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.athene.base64-secret}")
    private String secret;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, secret);
        return execution.execute(request, body);
    }
}
