package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("bamboo")
@Component
public class BambooAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Value("${artemis.continuous-integration.password}")
    private String bambooPassword;

    @Value("${artemis.continuous-integration.token:#{null}}")
    private Optional<String> bambooToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        if (request.getHeaders().getAccept().isEmpty()) {
            request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        }
        if (request.getHeaders().getContentType() == null) {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }

        // MediaType.TEXT_HTML does not support token based authentication, we have to use basic auth then or we need to use cookie authentication

        // TODO:

        // prefer bamboo token if it is available
        // if (bambooToken.isPresent()) {
        // request.getHeaders().setBearerAuth(bambooToken.get());
        // }
        // else {
        request.getHeaders().setBasicAuth(bambooUser, bambooPassword);
        // }
        return execution.execute(request, body);
    }
}
