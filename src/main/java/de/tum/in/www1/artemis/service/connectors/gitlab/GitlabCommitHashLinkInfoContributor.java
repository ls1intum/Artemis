package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.net.URL;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("gitlab")
public class GitlabCommitHashLinkInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Value("${artemis.version-control.commit-hash-template-path:#{null}}")
    private Optional<String> gitlabCommitHashPathTemplate;

    @Override
    public void contribute(Info.Builder builder) {
        if (gitlabCommitHashPathTemplate.isPresent()) {
            var commitHashUrlTemplate = gitlabServerUrl + gitlabCommitHashPathTemplate.get();
            builder.withDetail(Constants.INFO_COMMIT_HASH_URL_DETAIL, commitHashUrlTemplate);
        }
    }
}
