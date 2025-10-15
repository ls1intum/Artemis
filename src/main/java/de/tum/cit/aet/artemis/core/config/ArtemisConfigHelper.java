package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ATLAS_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.HYPERION_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.NEBULA_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSKEY_ENABLED_PROPERTY_NAME;

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
     * Check if the Nebula module is enabled.
     *
     * @param environment the Spring environment
     * @return true if the Atlas module is enabled, false otherwise
     */
    public boolean isNebulaEnabled(Environment environment) {
        return getPropertyOrExitArtemis(NEBULA_ENABLED_PROPERTY_NAME, environment);
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

    private boolean getPropertyOrExitArtemis(String key, Environment environment) {
        Boolean value = environment.getProperty(key, Boolean.class);
        if (value == null) {
            throw new RuntimeException(
                    String.format("Property %s not found in Artemis configuration. Make sure to add it to your application.yml-file. Allowed values: true, false", key));
        }
        return value;
    }
}
