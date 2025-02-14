package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.builder.CustomProperty;

public class EurekaClientEnabledProperty implements CustomProperty {

    @Override
    public String getPropertyName() {
        return "eureka.client.enabled";
    }

    @Override
    public Object getPropertyValue(ConfigurableEnvironment environment) {
        if (isBuildAgentOnlyMode(environment)) {
            return true;
        }

        // here can be more validation (isMultinode, etc.)

        return true;
    }
}
