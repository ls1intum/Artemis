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
        return isProfileActive(Constants.PROFILE_LOCALVC) || isProfileActive(Constants.PROFILE_LOCALCI);
    }

    public boolean isBamboo() {
        return isProfileActive("bamboo");
    }

    private boolean isProfileActive(String profile) {
        return Set.of(this.environment.getActiveProfiles()).contains(profile);
    }
}
