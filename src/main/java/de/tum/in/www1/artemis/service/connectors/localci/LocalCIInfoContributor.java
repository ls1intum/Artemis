package de.tum.in.www1.artemis.service.connectors.localci;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile("localci")
public class LocalCIInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Local CI");
    }
}
