package de.tum.cit.aet.artemis.service.connectors.gitlabci;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Component
@Profile("gitlabci")
public class GitLabCIInfoContributor implements InfoContributor {

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        // TODO: Not defined in https://github.com/ls1intum/Artemis/blob/develop/src/main/webapp/app/exercises/programming/shared/utils/programming-exercise.utils.ts#L24-L28
        final var buildPlanURLTemplate = artemisServerUrl + "/api/public/programming-exercises/{exerciseId}/build-plan";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);

        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "GitLab CI");
    }
}
