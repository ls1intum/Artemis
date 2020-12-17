package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
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

    private static final Logger log = LoggerFactory.getLogger(JenkinsAuthorizationInterceptor.class);

    @Value("${artemis.continuous-integration.user}")
    private String username;

    @Value("${artemis.continuous-integration.password}")
    private String password;

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsURL;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, @NotNull byte[] body, @NotNull ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBasicAuth(username, password);
        if (useCrumb) {
            setCrumb(request.getHeaders());
        }
        return execution.execute(request, body);
    }

    private void setCrumb(final HttpHeaders headersToAuthenticate) {
        try {
            JenkinsHttpClient jenkinsHttpClient = new JenkinsHttpClient(jenkinsURL.toURI(), username, password);
            Crumb crumb = jenkinsHttpClient.get("/crumbIssuer/api/json", Crumb.class);
            if (crumb != null) {
                headersToAuthenticate.add(crumb.getCrumbRequestField(), crumb.getCrumb());
            }
        }
        catch (IOException | URISyntaxException e) {
            log.warn("Cannot get crumb from Jenkins's crumb issuer: ", e);
        }
    }
}
