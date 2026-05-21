package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for providing information about the enabled module features.
 * This is required to report the enabled features to the client for features that are evaluated based on
 * Spring properties.
 * This is complementary with Spring profiles. It's a similar approach, but the support for active profiles
 * is supported by Spring out-of-the-box.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class ModuleFeatureInfoContributor implements InfoContributor {

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper;

    public ModuleFeatureInfoContributor(Environment environment) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public void contribute(Info.Builder builder) {
        List<String> enabledArtemisFeatures = artemisConfigHelper.getEnabledFeatures(environment);
        builder.withDetail(ACTIVE_MODULE_FEATURES, enabledArtemisFeatures);
    }
}
