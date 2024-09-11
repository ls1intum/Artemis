package de.tum.cit.aet.artemis.service.connectors.jenkins;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;

@Component
@Profile("jenkins")
public class JenkinsInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URL JENKINS_SERVER_URL;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = JENKINS_SERVER_URL + "/job/{projectKey}/job/{buildPlanId}";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);

        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Jenkins");
    }
}
