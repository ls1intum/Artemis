package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.ArtemisConstants;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Helper service for checking which profiles are active
 */
@Profile(PROFILE_CORE)
@Lazy
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
        return isProfileActive(ArtemisConstants.SPRING_PROFILE_DEVELOPMENT);
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
     * Checks if the jenkins profile is active
     *
     * @return true if the jenkins profile is active, false otherwise
     */
    public boolean isJenkinsActive() {
        return isProfileActive("jenkins");
    }

    /**
     * Checks if the local CI profile is active
     *
     * @return true if the local CI profile is active, false otherwise
     */
    public boolean isLocalCIActive() {
        return isProfileActive(Constants.PROFILE_LOCALCI);
    }

    // Sub-system profiles

    /**
     * Checks if the production profile is active
     *
     * @return true if the production profile is active, false otherwise
     */
    public boolean isProductionActive() {
        return isProfileActive(ArtemisConstants.SPRING_PROFILE_PRODUCTION);
    }
}
