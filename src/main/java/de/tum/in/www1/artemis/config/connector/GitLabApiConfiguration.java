package de.tum.in.www1.artemis.config.connector;

import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URL;

@Configuration
@Profile("gitlab")
public class GitLabApiConfiguration {
    @Value("${artemis.version-control.secret}")
    private String GITLAB_PRIVATE_TOKEN;

    @Value("${artemis.version-control.url}")
    private URL GITLAB_SERVER_URL;

    @Bean
    public GitLabApi gitLabApi(){
        return new GitLabApi(GITLAB_SERVER_URL.toString(), GITLAB_PRIVATE_TOKEN);
    }
}
