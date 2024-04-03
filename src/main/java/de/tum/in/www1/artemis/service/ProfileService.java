package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import tech.jhipster.config.JHipsterConstants;

@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Service
public class ProfileService {

    private final Environment environment;

    public ProfileService(Environment environment) {
        this.environment = environment;
    }

    public boolean isDevActive() {
        return isProfileActive(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }

    public boolean isLocalVcsCiActive() {
        return isLocalVcsActive() || isLocalCiActive();
    }

    public boolean isGitlabCiOrJenkinsActive() {
        return isProfileActive("gitlabci") || isJenkinsActive();
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

    public boolean isAeolusActive() {
        return isProfileActive(Constants.PROFILE_AEOLUS);
    }

    /**
     * Checks if the jenkins profile is active
     *
     * @return true if the jenkins profile is active, false otherwise
     */
    public boolean isJenkinsActive() {
        return isProfileActive("jenkins");
    }

    private boolean isProfileActive(String profile) {
        return Set.of(this.environment.getActiveProfiles()).contains(profile);
    }

    public boolean isLtiActive() {
        return isProfileActive(Constants.PROFILE_LTI);
    }
}
