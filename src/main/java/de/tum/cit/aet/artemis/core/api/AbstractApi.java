package de.tum.cit.aet.artemis.core.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.core.exception.ModuleNotPresentException;

public abstract class AbstractApi {

    private final Environment environment;

    private final String profileName;

    public AbstractApi(Environment environment, String profileName) {
        this.environment = environment;
        this.profileName = profileName;
    }

    public boolean isActive() {
        return isActive(profileName);
    }

    // This method will be removed once we switch to using the profileName
    private boolean isActive(String profileName) {
        return environment.acceptsProfiles(Profiles.of(profileName));
    }

    /** @noinspection OptionalUsedAsFieldOrParameterType */
    protected <T> T getOrThrow(Optional<T> instance) {
        // noinspection UnnecessaryLocalVariable
        String moduleProfileName = PROFILE_CORE; // in the future, we will switch to profileName
        if (!isActive(moduleProfileName) || instance.isEmpty()) {
            throw new ModuleNotPresentException(profileName);
        }
        return instance.get();
    }
}
