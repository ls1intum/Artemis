package de.tum.cit.aet.artemis.core.config.runtime_property;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * The PropertyOverridePostProcessor class allows overriding properties of the Spring application before dependency injection is performed.
 * That means, we can define which Services/Repositories are autowired, as well as which potential AutoConfigurations are excluded.
 * <p>
 * By evaluating conditions on the currently active profiles and properties, we can define whether the property should be
 * overridden and with which value.
 * For that, it attaches a listener {@link ApplicationEnvironmentPreparedEvent} and applies the properties to the
 * Spring environment.
 */
public class PropertyOverridePostProcessor {

    private static final Logger log = LoggerFactory.getLogger(PropertyOverridePostProcessor.class);

    /**
     * Attaches the conflicting properties to the Spring application before dependency injection is performed.
     * See {@link PropertyOverridePostProcessor}.
     *
     * @param app the Spring application to attach the properties to
     */
    public void attachTo(SpringApplication app) {
        app.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            ConfigurableEnvironment environment = event.getEnvironment();

            PropertyBuilder propertyBuilder = new PropertyBuilder(environment);
            var conflictingProperties = propertyBuilder.getConflictingProperties();
            validateNoConflictingProperties(conflictingProperties);

            var enabledProperties = propertyBuilder.getEnabledProperties();
            applyConfig(enabledProperties, environment);
        });
    }

    private void validateNoConflictingProperties(Map<String, Stack<PropertyOverride>> conflictingProperties) {
        if (conflictingProperties.isEmpty()) {
            return;
        }

        conflictingProperties.forEach((name, values) -> {
            log.error("The property {} has conflicting values: {}", name, values);
        });
        throw new IllegalStateException("The following properties are conflicting: " + conflictingProperties);
    }

    /**
     * Appends properties to the environment.
     *
     * @param properties  the properties to append
     * @param environment the environment to append the properties to
     */
    private void applyConfig(Set<PropertyOverride> properties, ConfigurableEnvironment environment) {
        for (PropertyOverride property : properties) {
            var result = new MapPropertySource(this.getClass().getName(), Map.of(property.getPropertyName(), property.getPropertyValue(environment)));
            // Order of overrides (last-named wins) and order of property source (packaged application.yml -> application.yml next to JAR -> system environment)
            // Using SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, this injects directly after system environment vars (highest priority).
            // this is required to also allow properties to be passed as env-vars instead of only via application.yml-files
            environment.getPropertySources().addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, result);
        }
    }
}
