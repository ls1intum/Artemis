package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Interceptor for adding API key authentication to Hyperion REST requests.
 * Follows the same pattern as AthenaAuthorizationInterceptor and PyrisAuthorizationInterceptor.
 */
@Component
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final HyperionRestConfigurationProperties hyperionProperties;

    public HyperionAuthorizationInterceptor(HyperionRestConfigurationProperties hyperionProperties) {
        this.hyperionProperties = hyperionProperties;
    }

    @Override
    @NotNull
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (hyperionProperties.getApiKey() != null && !hyperionProperties.getApiKey().isEmpty()) {
            request.getHeaders().set(API_KEY_HEADER, hyperionProperties.getApiKey());
        }
        return execution.execute(request, body);
    }
}
