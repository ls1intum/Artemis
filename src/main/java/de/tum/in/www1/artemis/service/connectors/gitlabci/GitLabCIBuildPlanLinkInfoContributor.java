package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("gitlabci")
public class GitLabCIBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.version-control.url}")
    private URL gitlabServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        // TODO: Add a template build plan url. Jenkins and Bamboo each use specific wildcards, which are then interpreted in the client. Since GitLab CI has a other structure (no
        // build plan ids) a change in the client code is necessary.
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, gitlabServerUrl);
    }
}
