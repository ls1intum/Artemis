package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.conditions.ConditionHelper.isBuildAgentOnlyMode;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.builder.CustomProperty;

public class ManagementEndpointHealthDetailsProperty implements CustomProperty {

    @Override
    public String getPropertyName() {
        return "management.endpoint.health.show-details";
    }

    @Override
    public Object getPropertyValue(ConfigurableEnvironment environment) {
        return "never";
    }

    @Override
    public boolean enabled(ConfigurableEnvironment environment) {
        return isBuildAgentOnlyMode(environment);
    }
}
