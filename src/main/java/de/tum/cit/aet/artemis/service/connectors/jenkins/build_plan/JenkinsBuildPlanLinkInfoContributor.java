package de.tum.cit.aet.artemis.service.connectors.jenkins.build_plan;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Profile("jenkins")
public class JenkinsBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URL jenkinsServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = jenkinsServerUrl + "/job/{projectKey}/job/{buildPlanId}";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);
    }
}
