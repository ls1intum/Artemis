package de.tum.cit.aet.artemis.core.config.builder;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Helper class for property configuration.
 */
public class PropertyConfigHelper {

    public static boolean isBuildAgentOnlyMode(ConfigurableEnvironment environment) {
        // WIP: switch to property "artemis.mode: buildagent-only"
        return environment.acceptsProfiles(Profiles.of(PROFILE_BUILDAGENT)) && !environment.acceptsProfiles(Profiles.of(PROFILE_CORE));
    }

    // This provides room for future extension
}
