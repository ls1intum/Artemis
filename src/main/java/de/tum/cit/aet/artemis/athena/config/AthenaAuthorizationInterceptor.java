package de.tum.cit.aet.artemis.athena.config;

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
import org.springframework.util.StringUtils;

/**
 * Adds the shared secret and the server URL to every request sent to Athena. Requests are refused locally while no secret
 * is configured.
 */
@Component
@Lazy
@Conditional(AthenaEnabled.class)
public class AthenaAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.athena.secret}")
    private String secret;

    @Value("${server.url}")
    private String artemisServerUrl;

    @NonNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (!StringUtils.hasText(secret)) {
            // Athena would not be able to tell this request apart from an unauthenticated one, so it is not sent at all.
            throw new IOException("The Athena secret is not configured, so the Athena service is not usable");
        }
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, secret);
        request.getHeaders().set("X-Server-URL", artemisServerUrl);
        return execution.execute(request, body);
    }
}
