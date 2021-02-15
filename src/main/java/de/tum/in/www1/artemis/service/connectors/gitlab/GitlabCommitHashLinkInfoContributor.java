package de.tum.in.www1.artemis.service.connectors.gitlab;

import java.net.URL;

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

    @Override
    public void contribute(Info.Builder builder) {
        String commitHashPathTemplate = "/{projectKey}/{buildPlanId}/-/commit/{commitHash}";
        String commitHashUrlTemplate = gitlabServerUrl + commitHashPathTemplate;
        builder.withDetail(Constants.INFO_COMMIT_HASH_URL_DETAIL, commitHashUrlTemplate);
    }
}
