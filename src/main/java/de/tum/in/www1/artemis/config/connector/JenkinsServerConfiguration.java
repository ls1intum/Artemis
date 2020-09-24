package de.tum.in.www1.artemis.config.connector;

import java.net.URISyntaxException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.offbytwo.jenkins.JenkinsServer;

@Configuration
@Profile("jenkins")
public class JenkinsServerConfiguration {

    @Value("${artemis.continuous-integration.user}")
    private String JENKINS_USER;

    @Value("${artemis.continuous-integration.password}")
    private String JENKINS_PASSWORD;

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    @Bean
    public JenkinsServer jenkinsServer() throws URISyntaxException {
        return new JenkinsServer(JENKINS_SERVER_URL.toURI(), JENKINS_USER, JENKINS_PASSWORD);
    }

}
