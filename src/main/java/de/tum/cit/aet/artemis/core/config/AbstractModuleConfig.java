package de.tum.cit.aet.artemis.core.config;

import java.util.List;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Abstract module configuration class.
 */
public abstract class AbstractModuleConfig {

    public AbstractModuleConfig(Environment environment, Set<Profiles> requiredProfiles) {
        // ToDo: Consider adding a profile/flag to disable this check in tests
        List<Profiles> requiredDisabledProfiles = requiredProfiles.stream().filter(profile -> !environment.acceptsProfiles(profile)).toList();
        if (!requiredDisabledProfiles.isEmpty()) {
            throw new IllegalStateException(
                    "Module requires the following profiles to be enabled: " + requiredProfiles + " but the following profiles are disabled: " + requiredDisabledProfiles);
        }
    }
}
