package de.tum.cit.aet.artemis.core.config.runtime_property.overrides;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;

import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverride;
import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverrideGroup;

public class EurekaClientOverrideGroup implements PropertyOverrideGroup {

    @Override
    public List<PropertyOverride> getProperties() {
        return List.of(new EurekaClientEnabledPropertyOverride());
    }

    static class EurekaClientEnabledPropertyOverride implements PropertyOverride {

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
}
