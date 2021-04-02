package de.tum.in.www1.artemis.config.connector;

import java.net.URISyntaxException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import com.offbytwo.jenkins.JenkinsServer;

@Configuration
@Profile("jenkins")
public class JenkinsServerConfiguration {

    @Value("${artemis.continuous-integration.user}")
    private String jenkinsUser;

    @Value("${artemis.continuous-integration.password}")
    private String jenkinsPassword;

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Bean
    public JenkinsServer jenkinsServer() throws URISyntaxException {
        return new JenkinsServer(jenkinsServerUrl.toURI(), jenkinsUser, jenkinsPassword);
    }

}
