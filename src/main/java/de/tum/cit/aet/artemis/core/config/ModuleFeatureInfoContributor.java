package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static de.tum.cit.aet.artemis.core.config.Constants.MODULE_FEATURE_ATLAS;
import static de.tum.cit.aet.artemis.core.config.Constants.MODULE_FEATURE_HYPERION;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    public ModuleFeatureInfoContributor(Environment environment,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures) {
        this.environment = environment;
        this.artemisConfigHelper = new ArtemisConfigHelper();
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;
    }

    @Override
    public void contribute(Info.Builder builder) {
        List<String> enabledArtemisFeatures = new ArrayList<>();
        if (artemisConfigHelper.isAtlasEnabled(environment)) {
            enabledArtemisFeatures.add(MODULE_FEATURE_ATLAS);
        }
        if (artemisConfigHelper.isHyperionEnabled(environment)) {
            enabledArtemisFeatures.add(MODULE_FEATURE_HYPERION);
        }
        if (artemisConfigHelper.isExamEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_EXAM);
        }
        if (artemisConfigHelper.isPlagiarismEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_PLAGIARISM);
        }
        if (artemisConfigHelper.isTextExerciseEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_TEXT);
        }
        if (artemisConfigHelper.isTutorialGroupEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_TUTORIALGROUP);
        }
        if (artemisConfigHelper.isPasskeyEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.FEATURE_PASSKEY);

            if (isPasskeyRequiredForAdministratorFeatures) {
                enabledArtemisFeatures.add(Constants.FEATURE_PASSKEY_REQUIRE_ADMIN);
            }
        }
        if (artemisConfigHelper.isNebulaEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_NEBULA);
        }
        if (artemisConfigHelper.isSharingEnabled(environment)) {
            enabledArtemisFeatures.add(Constants.MODULE_FEATURE_SHARING);
        }

        builder.withDetail(ACTIVE_MODULE_FEATURES, enabledArtemisFeatures);
    }
}
