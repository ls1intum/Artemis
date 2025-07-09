package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
public class CompatibleVersionsInfoContributor implements InfoContributor {

    private final ArtemisCompatibleVersionsConfiguration versionsConfiguration;

    public CompatibleVersionsInfoContributor(ArtemisCompatibleVersionsConfiguration versionsConfiguration) {
        this.versionsConfiguration = versionsConfiguration;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("compatibleVersions", versionsConfiguration);
    }
}
