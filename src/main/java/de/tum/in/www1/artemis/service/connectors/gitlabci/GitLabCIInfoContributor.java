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
public class GitLabCIInfoContributor implements InfoContributor {

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = artemisServerUrl + "/api/programming-exercises/{exerciseId}/build-plan";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "GitLab CI");
    }
}
