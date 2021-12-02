package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static de.tum.in.www1.artemis.config.Constants.INFO_BUILD_PLAN_URL_DETAIL;
import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_BAMBOO;

@Component
@Profile(SPRING_PROFILE_BAMBOO)
public class BambooBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = bambooServerUrl + "/browse/{buildPlanId}";
        builder.withDetail(INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);
    }
}
