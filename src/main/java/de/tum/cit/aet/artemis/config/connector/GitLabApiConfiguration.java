package de.tum.cit.aet.artemis.config.connector;

import java.net.URL;

import org.gitlab4j.api.GitLabApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

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
