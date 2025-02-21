package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static de.tum.cit.aet.artemis.core.config.Constants.MODULE_FEATURE_ATLAS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Profile(PROFILE_CORE)
@Component
public class ModuleFeatureInfoContributor implements InfoContributor {

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    public ModuleFeatureInfoContributor(Environment environment) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public void contribute(Info.Builder builder) {
        List<String> enabledArtemisFeatures = new ArrayList<>();
        if (artemisConfigHelper.isAtlasEnabled(environment)) {
            enabledArtemisFeatures.add(MODULE_FEATURE_ATLAS);
        }
        builder.withDetail(ACTIVE_MODULE_FEATURES, enabledArtemisFeatures);
    }
}
