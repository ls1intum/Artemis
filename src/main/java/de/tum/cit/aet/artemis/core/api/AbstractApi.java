package de.tum.cit.aet.artemis.core.api;

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
        return environment.acceptsProfiles(Profiles.of(profileName));
    }

    /** @noinspection OptionalUsedAsFieldOrParameterType */
    protected <T> T getOrThrow(Optional<T> instance) {
        if (!isActive() || instance.isEmpty()) {
            throw new ModuleNotPresentException(profileName);
        }
        return instance.get();
    }
}
