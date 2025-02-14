package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import de.tum.cit.aet.artemis.core.config.builder.ConflictingOverrideProperty;

public class LiquibaseEnabledProperty implements ConflictingOverrideProperty {

    @Override
    public String getPropertyName() {
        return "liquibase.enabled";
    }

    @Override
    public Object getPropertyValue(ConfigurableEnvironment environment) {
        return false;
    }

    @Override
    public boolean enabled(ConfigurableEnvironment environment) {
        return environment.acceptsProfiles(Profiles.of(PROFILE_BUILDAGENT)) && !environment.acceptsProfiles(Profiles.of(PROFILE_CORE));
    }
}
