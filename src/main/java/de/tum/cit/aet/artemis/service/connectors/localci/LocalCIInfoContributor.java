package de.tum.cit.aet.artemis.service.connectors.localci;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_LOCALCI;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.config.Constants;

@Component
@Profile(PROFILE_LOCALCI)
public class LocalCIInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        // Store name of the continuous integration system
        builder.withDetail(Constants.CONTINUOUS_INTEGRATION_NAME, "Local CI");
    }
}
