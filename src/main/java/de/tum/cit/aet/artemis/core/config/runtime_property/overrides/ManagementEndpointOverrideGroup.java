package de.tum.cit.aet.artemis.core.config.runtime_property.overrides;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;

import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverride;
import de.tum.cit.aet.artemis.core.config.runtime_property.PropertyOverrideGroup;

public final class ManagementEndpointOverrideGroup implements PropertyOverrideGroup {

    @Override
    public List<PropertyOverride> getProperties() {
        return List.of(
        // @formatter:off
            new ManagementEndpointHealthDetailsPropertyOverride(),
            new ManagementEndpointJhiMetricsEnabledPropertyOverride(),
            new ManagementEndpointProbesEnabledPropertyOverride(),
            new ManagementMetricsEnablePropertyOverride()
            // @formatter:on
        );
    }

    static class ManagementEndpointJhiMetricsEnabledPropertyOverride implements PropertyOverride {

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

    static class ManagementMetricsEnablePropertyOverride implements PropertyOverride {

        @Override
        public String getPropertyName() {
            return "management.metrics.enable.all";
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

    static class ManagementEndpointProbesEnabledPropertyOverride implements PropertyOverride {

        @Override
        public String getPropertyName() {
            return "management.endpoint.probes.enabled";
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

    static class ManagementEndpointHealthDetailsPropertyOverride implements PropertyOverride {

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
}
