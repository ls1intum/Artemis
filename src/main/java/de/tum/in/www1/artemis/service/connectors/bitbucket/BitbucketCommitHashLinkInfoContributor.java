package de.tum.in.www1.artemis.service.connectors.bitbucket;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("bitbucket")
public class BitbucketCommitHashLinkInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL bitbucketServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        String commitHashPathTemplate = "/projects/{projectKey}/repos/{buildPlanId}/commits/{commitHash}";
        String commitHashUrlTemplate = bitbucketServerUrl + commitHashPathTemplate;
        builder.withDetail(Constants.INFO_COMMIT_HASH_URL_DETAIL, commitHashUrlTemplate);
    }
}
