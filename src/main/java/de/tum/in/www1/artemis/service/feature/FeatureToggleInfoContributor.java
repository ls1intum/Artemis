package de.tum.in.www1.artemis.service.feature;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class FeatureToggleInfoContributor implements InfoContributor {

    private final FeatureService featureService;

    public FeatureToggleInfoContributor(FeatureService featureService) {
        this.featureService = featureService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("features", featureService.enabledFeatures());
    }
}
