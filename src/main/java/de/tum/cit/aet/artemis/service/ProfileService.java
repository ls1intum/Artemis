package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.config.Constants.PROFILE_SCHEDULING;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.config.Constants;
import tech.jhipster.config.JHipsterConstants;

/**
 * Helper service for checking which profiles are active
 */
@Profile(PROFILE_CORE)
@Service
public class ProfileService {

    private final Environment environment;

    public ProfileService(Environment environment) {
        this.environment = environment;
    }

    // General profiles

    /**
     * Checks if a given profile is active. Prefer to use the specific methods for the profiles
     *
     * @param profile the profile to check
     * @return true if the profile is active, false otherwise
     */
    public boolean isProfileActive(String profile) {
        return Set.of(this.environment.getActiveProfiles()).contains(profile);
    }

    /**
     * Checks if the development profile is active
     *
     * @return true if the development profile is active, false otherwise
     */
    public boolean isDevActive() {
        return isProfileActive(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }

    /**
     * Checks if the scheduling profile is active
     *
     * @return true if the scheduling profile is active, false otherwise
     */
    public boolean isSchedulingActive() {
        return isProfileActive(PROFILE_SCHEDULING);
    }

    // VC & CI profiles

    /**
     * Checks if the gitlabci or jenkins profile is active
     *
     * @return true if the gitlabci or jenkins profile is active, false otherwise
     */
    public boolean isGitlabCiOrJenkinsActive() {
        return isProfileActive("gitlabci") || isJenkinsActive();
    }

    /**
     * Checks if the jenkins profile is active
     *
     * @return true if the jenkins profile is active, false otherwise
     */
    public boolean isJenkinsActive() {
        return isProfileActive("jenkins");
    }

    /**
     * Checks if the local VCS or CI profile is active
     *
     * @return true if the local VCS or CI profile is active, false otherwise
     */
    public boolean isLocalVcsCiActive() {
        return isLocalVcsActive() || isLocalCiActive();
    }

    /**
     * Checks if the local CI profile is active
     *
     * @return true if the local CI profile is active, false otherwise
     */
    public boolean isLocalCiActive() {
        return isProfileActive(Constants.PROFILE_LOCALCI);
    }

    /**
     * Checks if the local VC profile is active
     *
     * @return true if the local VC profile is active, false otherwise
     */
    public boolean isLocalVcsActive() {
        return isProfileActive(Constants.PROFILE_LOCALVC);
    }

    // Sub-system profiles

    /**
     * Checks if the aeolus profile is active
     *
     * @return true if the aeolus profile is active, false otherwise
     */
    public boolean isAeolusActive() {
        return isProfileActive(Constants.PROFILE_AEOLUS);
    }

    /**
     * Checks if the lti profile is active
     *
     * @return true if the lti profile is active, false otherwise
     */
    public boolean isLtiActive() {
        return isProfileActive(Constants.PROFILE_LTI);
    }

    /**
     * Checks if the production profile is active
     *
     * @return true if the production profile is active, false otherwise
     */
    public boolean isProductionActive() {
        return isProfileActive(JHipsterConstants.SPRING_PROFILE_PRODUCTION);
    }
}
