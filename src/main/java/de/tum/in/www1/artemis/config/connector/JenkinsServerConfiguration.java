package de.tum.in.www1.artemis.config.connector;

import com.offbytwo.jenkins.JenkinsServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URISyntaxException;
import java.net.URL;

@Configuration
@Profile("jenkins")
public class JenkinsServerConfiguration {
    @Value("${artemis.continuous-integration.user}")
    private String JENKINS_USER;

    @Value("${artemis.continuous-integration.password}")
    private String JENKINS_PASSWORD;

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    @Value("${jenkins.use-crumb:#{true}}")
    private boolean useCrumb;

    @Bean
    public JenkinsServer jenkinsServer() throws URISyntaxException {
        return new JenkinsServer(JENKINS_SERVER_URL.toURI(), JENKINS_USER, JENKINS_PASSWORD);
    }

}
