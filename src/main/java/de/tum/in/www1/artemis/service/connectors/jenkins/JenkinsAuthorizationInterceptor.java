package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.validation.constraints.NotNull;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import com.offbytwo.jenkins.client.JenkinsHttpClient;
import com.offbytwo.jenkins.model.Crumb;

@Profile("jenkins")
@Component
public class JenkinsAuthorizationInterceptor implements ClientHttpRequestInterceptor {

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsURL;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    private JenkinsHttpClient jenkinsClient;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBasicAuth(username, password);
        if (!useCrumb) {
            setCrumb(request.getHeaders());
        }
        return execution.execute(request, body);
    }

    private void setCrumb(final HttpHeaders headersToAuthenticate) {
        /*
         * final var headers = new HttpHeaders(); headers.setBasicAuth(username, password); final var entity = new HttpEntity<>(headers);
         */
        try {
            JenkinsHttpClient jenkinsHttpClient = new JenkinsHttpClient(jenkinsURL.toURI(), username, password);
            Crumb crumb = jenkinsHttpClient.get("/crumbIssuer", Crumb.class);
            if (crumb != null) {
                headersToAuthenticate.add(crumb.getCrumbRequestField(), crumb.getCrumb());

            }
        }
        catch (IOException | URISyntaxException e) {
            LoggerFactory.getLogger(JenkinsAuthorizationInterceptor.class).warn("Cannot get crumb from Jenkins crumbIssuer: ", e);
        }

        /*
         * final var response = new RestTemplate().exchange(jenkinsURL.toString() + "/crumbIssuer/api/json", HttpMethod.GET, entity, JsonNode.class); final var sessionId =
         * response.getHeaders().get("Set-Cookie").get(0); headersToAuthenticate.add("Jenkins-Crumb", response.getBody().get("crumb").asText()); headersToAuthenticate.add("Cookie",
         * sessionId);
         */
    }
}
