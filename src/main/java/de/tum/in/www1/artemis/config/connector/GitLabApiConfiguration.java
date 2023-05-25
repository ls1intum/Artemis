package de.tum.in.www1.artemis.config.connector;

import java.net.URL;

import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("gitlab | gitlabci")
public class GitLabApiConfiguration {

    @Value("${artemis.version-control.token}")
    private String gitlabPrivateToken;

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Bean
    public GitLabApi gitLabApi() {
        return new GitLabApi(gitlabServerUrl.toString(), gitlabPrivateToken);
    }
}
