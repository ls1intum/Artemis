package de.tum.in.www1.artemis.service.connectors.gitlabci;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("gitlabci")
public class GitLabCIBuildPlanLinkInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        // TODO: Template URL for build plan API
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, GitLabCIService.CI_CONFIG_URL);
    }
}
