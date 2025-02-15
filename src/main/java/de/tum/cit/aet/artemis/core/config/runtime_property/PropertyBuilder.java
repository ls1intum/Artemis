package de.tum.cit.aet.artemis.core.config.runtime_property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.springframework.core.env.ConfigurableEnvironment;

import de.tum.cit.aet.artemis.core.config.runtime_property.overrides.AutoConfigurationExclusionOverrideGroup;
import de.tum.cit.aet.artemis.core.config.runtime_property.overrides.CloudLoadBalancerOverrideGroup;
import de.tum.cit.aet.artemis.core.config.runtime_property.overrides.EurekaClientOverrideGroup;
import de.tum.cit.aet.artemis.core.config.runtime_property.overrides.LiquibaseOverrideGroup;
import de.tum.cit.aet.artemis.core.config.runtime_property.overrides.ManagementEndpointOverrideGroup;

/**
 * Builder class to store and retrieve properties that are enabled based on the environment.
 */
public class PropertyBuilder {

    /**
     * Logical blocks of overrides for the sake of readability. There are no clear guidelines on how to structure, but
     * you have to make sure that you don't override the same property multiple times.
     */
    private static final List<PropertyOverrideGroup> overrides = List.of(
    // @formatter:off
        new ManagementEndpointOverrideGroup(),
        new EurekaClientOverrideGroup(),
        new AutoConfigurationExclusionOverrideGroup(),
        new LiquibaseOverrideGroup(),
        new CloudLoadBalancerOverrideGroup()
        // @formatter:on
    );

    /**
     * Stores all properties that would be enabled based on the environment.
     * The key is the property name and the value is a stack of properties which condition evaluated to true.
     * The top stack element is the property that has been added latest and would be enabled.
     */
    private static final Map<String, Stack<PropertyOverride>> storedProperties = new HashMap<>();

    public PropertyBuilder(ConfigurableEnvironment environment) {
        for (PropertyOverrideGroup override : overrides) {
            override.getProperties().forEach(p -> storeProperty(p, environment));
        }
    }

    private void storeProperty(PropertyOverride enabledProperty, ConfigurableEnvironment environment) {
        String propertyName = enabledProperty.getPropertyName();
        storedProperties.computeIfAbsent(propertyName, k -> new Stack<>()).add(enabledProperty);
    }

    /**
     * Returns all properties that would be enabled based on the environment.
     *
     * @return a set of properties that would be enabled
     */
    public Set<PropertyOverride> getEnabledProperties() {
        return storedProperties.values().stream().map(Stack::peek).collect(Collectors.toSet());
    }

    /**
     * Returns all properties that would be enabled based on the environment.
     *
     * @return a map of properties that would be enabled
     */
    public Map<String, Stack<PropertyOverride>> getConflictingProperties() {
        return storedProperties.entrySet().stream().filter(e -> e.getValue().size() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
