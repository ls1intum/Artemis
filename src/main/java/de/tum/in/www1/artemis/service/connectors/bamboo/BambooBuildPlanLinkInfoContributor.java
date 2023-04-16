package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("bamboo")
public class BambooBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URL bambooServerUrl;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = bambooServerUrl + "/browse/{buildPlanId}";
        builder.withDetail(Constants.INFO_BUILD_PLAN_URL_DETAIL, buildPlanURLTemplate);

        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Bamboo");
    }
}
