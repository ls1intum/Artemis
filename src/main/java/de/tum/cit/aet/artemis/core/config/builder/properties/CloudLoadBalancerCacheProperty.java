package de.tum.cit.aet.artemis.core.config.builder.properties;

import static de.tum.cit.aet.artemis.core.config.builder.PropertyConfigHelper.isBuildAgentOnlyMode;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.builder.ConflictingOverrideProperty;

public class CloudLoadBalancerCacheProperty implements ConflictingOverrideProperty {

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
