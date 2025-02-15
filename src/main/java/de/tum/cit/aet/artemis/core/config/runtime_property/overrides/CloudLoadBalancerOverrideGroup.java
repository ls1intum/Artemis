package de.tum.cit.aet.artemis.core.config.runtime_property.overrides;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;

import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverride;
import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverrideGroup;

public class CloudLoadBalancerOverrideGroup implements PropertyOverrideGroup {

    @Override
    public List<PropertyOverride> getProperties() {
        return List.of(new CloudLoadBalancerPropertyOverride());
    }

    static class CloudLoadBalancerPropertyOverride implements PropertyOverride {

        @Override
        public String getPropertyName() {
            return "spring.cloud.loadbalancer.cache.enabled";
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

}
