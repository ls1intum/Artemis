package de.tum.in.www1.artemis.service.connectors.bitbucket;

import static org.springframework.http.HttpMethod.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Profile("bitbucket")
@Component
public class BitbucketAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.version-control.user}")
    private String bitbucketUser;

    @Value("${artemis.version-control.password}")
    private String bitbucketPassword;

    @Value("${artemis.version-control.token:#{null}}")
    private Optional<String> bitbucketToken;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        if (request.getHeaders().getAccept().isEmpty()) {
            request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        }
        if (request.getHeaders().getContentType() == null) {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        }

        // prefer bitbucket token if it is available
        if (bitbucketToken.isPresent() && !needsBasicAuth(request)) {
            request.getHeaders().setBearerAuth(bitbucketToken.get());
        }
        else {
            // the create project request needs basic auth and does not work with personal tokens
            request.getHeaders().setBasicAuth(bitbucketUser, bitbucketPassword);
        }
        return execution.execute(request, body);
    }

    private static boolean needsBasicAuth(HttpRequest request) {
        return isCreateProjectRequest(request) || isCreateOrEditOrDeleteUserRequest(request) || isAddUserToGroupsRequest(request);
    }

    private static boolean isCreateProjectRequest(HttpRequest request) {
        return request.getURI().toString().endsWith("latest/projects") && POST.equals(request.getMethod());
    }

    private static boolean isCreateOrEditOrDeleteUserRequest(HttpRequest request) {
        return request.getURI().toString().contains("latest/admin/users")
                && (POST.equals(request.getMethod()) || PUT.equals(request.getMethod()) || DELETE.equals(request.getMethod()));
    }

    private static boolean isAddUserToGroupsRequest(HttpRequest request) {
        return request.getURI().toString().endsWith("latest/admin/users/add-groups") && POST.equals(request.getMethod());
    }
}
