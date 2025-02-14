package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.builder.CustomProperty;

public class ManagementEndpointJhiMetricsEnabledProperty implements CustomProperty {

    @Override
    public String getPropertyName() {
        return "management.endpoint.jhimetrics.enabled";
    }

    @Override
    public Object getPropertyValue(ConfigurableEnvironment environment) {
        return false;
    }

    @Override
    public boolean enabled(ConfigurableEnvironment environment) {
        return isBuildAgentOnlyMode(environment);
    }
}
