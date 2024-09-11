package de.tum.cit.aet.artemis.service.feature;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
public class FeatureToggleInfoContributor implements InfoContributor {

    private final FeatureToggleService featureToggleService;

    public FeatureToggleInfoContributor(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("features", featureToggleService.enabledFeatures());
    }
}
