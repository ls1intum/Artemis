package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Helper service for checking which module features are active
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ModuleFeatureService {

    private final Environment environment;

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    public ModuleFeatureService(Environment environment) {
        this.environment = environment;
    }

    /**
     * Check if the Passkey feature is enabled.
     *
     * @return true if the Passkey feature is enabled, false otherwise
     */
    public boolean isPasskeyEnabled() {
        return artemisConfigHelper.isPasskeyEnabled(environment);
    }

    /**
     * Check if the Sharing feature is enabled.
     *
     * @return true if the Sharing feature is enabled, false otherwise
     */
    public boolean isSharingEnabled() {
        return artemisConfigHelper.isSharingEnabled(environment);
    }

    /**
     * Check if the Atlas module is enabled.
     *
     * @return true if the Atlas module is enabled, false otherwise
     */
    public boolean isAtlasEnabled() {
        return artemisConfigHelper.isAtlasEnabled(environment);
    }

    /**
     * Check if the Hyperion module is enabled.
     *
     * @return true if the Hyperion module is enabled, false otherwise
     */
    public boolean isHyperionEnabled() {
        return artemisConfigHelper.isHyperionEnabled(environment);
    }

    /**
     * Check if the exam module is enabled.
     *
     * @return true if the exam module is enabled, false otherwise
     */
    public boolean isExamEnabled() {
        return artemisConfigHelper.isExamEnabled(environment);
    }

    /**
     * Check if the Plagiarism module is enabled.
     *
     * @return true if the Plagiarism module is enabled, false otherwise
     */
    public boolean isPlagiarismEnabled() {
        return artemisConfigHelper.isPlagiarismEnabled(environment);
    }

    /**
     * Check if the text module is enabled.
     *
     * @return true if the text module is enabled, false otherwise
     */
    public boolean isTextExerciseEnabled() {
        return artemisConfigHelper.isTextExerciseEnabled(environment);
    }

    /**
     * Check if the tutorial group feature is enabled.
     *
     * @return true if the tutorial group feature is enabled, false otherwise
     */
    public boolean isTutorialGroupEnabled() {
        return artemisConfigHelper.isTutorialGroupEnabled(environment);
    }

    /**
     * Check if the Nebula module is enabled.
     *
     * @return true if the Nebula module is enabled, false otherwise
     */
    public boolean isNebulaEnabled() {
        return artemisConfigHelper.isNebulaEnabled(environment);
    }

    /**
     * Check if the video upload feature for lecture units is enabled.
     *
     * @return true if video upload is enabled, false otherwise
     */
    public boolean isVideoUploadEnabled() {
        return artemisConfigHelper.isVideoUploadEnabled(environment);
    }

    /**
     * Get the maximum video file size for lecture unit uploads.
     *
     * @return the maximum video file size in bytes
     */
    public long getVideoUploadMaxFileSize() {
        return artemisConfigHelper.getVideoUploadMaxFileSize(environment);
    }
}
