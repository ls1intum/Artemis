package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import tech.jhipster.config.JHipsterConstants;

@Service
public class ProfileService {

    private final Environment environment;

    public ProfileService(Environment environment) {
        this.environment = environment;
    }

    public boolean isDev() {
        return isProfileActive(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT);
    }

    public boolean isLocalVcsCi() {
        return isLocalVcs() || isLocalCi();
    }

    public boolean isBamboo() {
        return isProfileActive("bamboo");
    }

    public boolean isGitlabCiOrJenkins() {
        return isProfileActive("gitlabci") || isJenkins();
    }

    /**
     * Checks if the local CI profile is active
     *
     * @return true if the local CI profile is active, false otherwise
     */
    public boolean isLocalCi() {
        return isProfileActive(Constants.PROFILE_LOCALCI);
    }

    /**
     * Checks if the local VC profile is active
     *
     * @return true if the local VC profile is active, false otherwise
     */
    public boolean isLocalVcs() {
        return isProfileActive(Constants.PROFILE_LOCALVC);
    }

    /**
     * Checks if the jenkins profile is active
     *
     * @return true if the jenkins profile is active, false otherwise
     */
    public boolean isJenkins() {
        return isProfileActive("jenkins");
    }

    private boolean isProfileActive(String profile) {
        return Set.of(this.environment.getActiveProfiles()).contains(profile);
    }
}
