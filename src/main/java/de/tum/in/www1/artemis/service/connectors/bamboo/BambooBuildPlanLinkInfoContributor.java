package de.tum.in.www1.artemis.service.connectors.bamboo;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class BambooBuildPlanLinkInfoContributor implements InfoContributor {

    @Value("${artemis.continuous-integration.url}")
    private URL BAMBOO_SERVER_URL;

    @Override
    public void contribute(Info.Builder builder) {
        final var buildPlanURLTemplate = BAMBOO_SERVER_URL + "/browse/{buildPlanId}";

        builder.withDetail("buildPlanURLTemplate", buildPlanURLTemplate);
    }
}
