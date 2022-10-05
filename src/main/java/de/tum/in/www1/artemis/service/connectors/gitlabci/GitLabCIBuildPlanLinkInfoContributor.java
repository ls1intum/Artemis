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

    @Value("${server.url}")
    private URL artemisServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        // TODO: Not defined in https://github.com/ls1intum/Artemis/blob/develop/src/main/webapp/app/exercises/programming/shared/utils/programming-exercise.utils.ts#L24-L28
        final var buildPlanURLTemplate = artemisServerUrl + "/api/programming-exercises/{exerciseId}/build-plan";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);
    }
}
