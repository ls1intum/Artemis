package de.tum.cit.aet.artemis.iris.config;

import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@Lazy
@Conditional(IrisEnabled.class)
public class PyrisAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.iris.secret-token}")
    private String secret;

    @NonNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, secret);
        return execution.execute(request, body);
    }
}
