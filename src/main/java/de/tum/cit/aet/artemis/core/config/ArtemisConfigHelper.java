package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.APOLLON_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.ATLAS_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.IRIS_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.LTI_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.NEBULA_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSKEY_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.SHARING_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.THEIA_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.WEAVIATE_ENABLED_PROPERTY_NAME;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;

/**
 * Helper class for property configuration, in particular for determining conditions
 * whether services should be enabled or not.
 * This bridges the gap between the condition classes and the actual property values.
 */
public class ArtemisConfigHelper {

    /**
     * Check if the Passkey feature is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Passkey feature is enabled, false otherwise
     */
    public boolean isPasskeyEnabled(Environment environment) {
        return getPropertyOrExitArtemis(PASSKEY_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if passkey is required for administrator features.
     * This only applies when passkey is enabled.
     *
     * @param environment the Spring environment
     * @return true if passkey is required for administrator features, false otherwise
     */
    public boolean isPasskeyRequiredForAdmin(Environment environment) {
        return environment.getProperty(Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME, Boolean.class, false);
    }

    /**
     * Check if the Sharing feature is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Sharing feature is enabled, false otherwise
     */
    public boolean isSharingEnabled(Environment environment) {
        return getPropertyOrExitArtemis(SHARING_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Atlas module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Atlas module is enabled, false otherwise
     */
    public boolean isAtlasEnabled(Environment environment) {
        return getPropertyOrExitArtemis(ATLAS_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Hyperion module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Hyperion module is enabled, false otherwise
     */
    public boolean isHyperionEnabled(Environment environment) {
        return getPropertyOrExitArtemis(HYPERION_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Iris module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Iris module is enabled, false otherwise
     */
    public boolean isIrisEnabled(Environment environment) {
        return getPropertyOrExitArtemis(IRIS_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the exam module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the exam module is enabled, false otherwise
     */
    public boolean isExamEnabled(Environment environment) {
        return getPropertyOrExitArtemis(EXAM_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Plagiarism module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Plagiarism module is enabled, false otherwise
     */
    public boolean isPlagiarismEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.PLAGIARISM_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the text module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the text module is enabled, false otherwise
     */
    public boolean isTextExerciseEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.TEXT_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the modeling module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the modeling module is enabled, false otherwise
     */
    public boolean isModelingEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.MODELING_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the file upload module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the file upload module is enabled, false otherwise
     */
    public boolean isFileUploadEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.FILEUPLOAD_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the lecture module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the lecture module is enabled, false otherwise
     */
    public boolean isLectureEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.LECTURE_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the tutorial group feature is enabled.
     *
     * @param environment the Spring environment
     * @return true if the tutorial group feature is enabled, false otherwise
     */
    public boolean isTutorialGroupEnabled(Environment environment) {
        return getPropertyOrExitArtemis(Constants.TUTORIAL_GROUP_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Nebula module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Nebula module is enabled, false otherwise
     */
    public boolean isNebulaEnabled(Environment environment) {
        return getPropertyOrExitArtemis(NEBULA_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the LTI module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the LTI module is enabled, false otherwise
     */
    public boolean isLtiEnabled(Environment environment) {
        return getPropertyOrExitArtemis(LTI_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if Apollon PDF export is enabled.
     * This only controls PDF export; the modeling editor is controlled by {@link #isModelingEnabled(Environment)}.
     *
     * @param environment the Spring environment
     * @return true if Apollon PDF export is enabled, false otherwise
     */
    public boolean isApollonEnabled(Environment environment) {
        return getPropertyOrExitArtemis(APOLLON_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Theia module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Theia module is enabled, false otherwise
     */
    public boolean isTheiaEnabled(Environment environment) {
        return getPropertyOrExitArtemis(THEIA_ENABLED_PROPERTY_NAME, environment);
    }

    /**
     * Check if the Weaviate integration is enabled.
     * Defaults to false if not configured, as this is a development feature.
     *
     * @param environment the Spring environment
     * @return true if the Weaviate integration is enabled, false otherwise
     */
    public boolean isWeaviateEnabled(Environment environment) {
        // For now this is a development feature only, so we default to false instead of throwing an error if the property is missing
        return environment.getProperty(WEAVIATE_ENABLED_PROPERTY_NAME, Boolean.class, false);
    }

    /**
     * Gets the list of all enabled module features based on configuration.
     *
     * @param environment the Spring environment
     * @return list of enabled feature names
     */
    public List<String> getEnabledFeatures(Environment environment) {
        List<String> enabledFeatures = new ArrayList<>();

        if (isAtlasEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_ATLAS);
        }
        if (isHyperionEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_HYPERION);
        }
        if (isIrisEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_IRIS);
        }
        if (isExamEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_EXAM);
        }
        if (isPlagiarismEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_PLAGIARISM);
        }
        if (isTextExerciseEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_TEXT);
        }
        if (isModelingEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_MODELING);
        }
        if (isFileUploadEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_FILEUPLOAD);
        }
        if (isLectureEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_LECTURE);
        }
        if (isTutorialGroupEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_TUTORIALGROUP);
        }
        if (isPasskeyEnabled(environment)) {
            enabledFeatures.add(Constants.FEATURE_PASSKEY);
            if (isPasskeyRequiredForAdmin(environment)) {
                enabledFeatures.add(Constants.FEATURE_PASSKEY_REQUIRE_ADMIN);
            }
        }
        if (isNebulaEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_NEBULA);
        }
        if (isSharingEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_SHARING);
        }
        if (isLtiEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_LTI);
        }
        if (isApollonEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_APOLLON);
        }
        if (isTheiaEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_THEIA);
        }

        return enabledFeatures;
    }

    private boolean getPropertyOrExitArtemis(String key, Environment environment) {
        Boolean value = environment.getProperty(key, Boolean.class);
        if (value == null) {
            throw new RuntimeException(
                    String.format("Property %s not found in Artemis configuration. Make sure to add it to your application.yml-file. Allowed values: true, false", key));
        }
        return value;
    }
}
