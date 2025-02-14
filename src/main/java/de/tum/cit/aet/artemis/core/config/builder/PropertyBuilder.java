package de.tum.cit.aet.artemis.core.config.builder;

import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isBuildAgentOnlyMode;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isGitLabCIEnabled;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isGitLabEnabled;
import static de.tum.cit.aet.artemis.core.config.conditions.ArtemisConfigHelper.isJenkinsEnabled;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import de.tum.cit.aet.artemis.core.config.builder.properties.AutoConfigurationExclusionProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.CloudLoadBalancerCacheProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.EurekaClientEnabledProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.LiquibaseEnabledProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.ManagementEndpointHealthDetailsProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.ManagementEndpointJhiMetricsEnabledProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.ManagementEndpointProbesEnabledProperty;
import de.tum.cit.aet.artemis.core.config.builder.properties.ManagementMetricsEnableProperty;

/**
 * This class is responsible for attaching the conflicting properties to the Spring application.
 * On a high-level, this could be understood as part of dependency injection as we literally define the components
 * that will be autowired.
 */
public class PropertyBuilder {

    /**
     * Note: One could also think about creating another layer of abstraction for the properties.
     * It could be useful to group ManagementEndpointOverrides together.
     * Contrary, the current approach is more flexible and - despite boilerplate - easier to understand and maintain.
     */
    private static final List<CustomProperty> properties = List.of(
    // @formatter:off
        new AutoConfigurationExclusionProperty(),
        new LiquibaseEnabledProperty(),
        new CloudLoadBalancerCacheProperty(),
        new ManagementEndpointHealthDetailsProperty(),
        new EurekaClientEnabledProperty(),
        new ManagementEndpointJhiMetricsEnabledProperty(),
        new ManagementEndpointProbesEnabledProperty(),
        new ManagementMetricsEnableProperty()
        // @formatter:on
    );

    /**
     * Attaches the conflicting properties to the Spring application.
     * This is performed by adding a listener to the application that listens for the ApplicationEnvironmentPreparedEvent.
     *
     * @param app the Spring application
     */
    public void attachPostProcessor(SpringApplication app) {
        app.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            ConfigurableEnvironment environment = event.getEnvironment();
            validateConfig(environment);

            var enabledProperties = properties.stream().filter(s -> s.enabled(environment)).toList();
            for (CustomProperty property : enabledProperties) {
                appendProperty(property, environment);
            }
        });
    }

    /**
     * Custom logic to validate configurations.
     * For instance, running BUILDAGENT on a node with a dedicated CI like Jenkins or GitLabCI enabled make little sense.
     * Note: We might want to move this to a different class.
     */
    private void validateConfig(ConfigurableEnvironment environment) {
        if (isBuildAgentOnlyMode(environment)) {
            if (isGitLabEnabled(environment) || isJenkinsEnabled(environment)) {
                throw new IllegalStateException("The build agent only mode is not allowed with the gitlab or jenkins profile.");
            }
        }

        if (isJenkinsEnabled(environment) && isGitLabCIEnabled(environment)) {
            throw new IllegalStateException("The jenkins and gitlab profiles cannot be active at the same time.");
        }
        // further checks can be added here
    }

    /**
     * Appends the property to the environment.
     *
     * @param property    the property to append
     * @param environment the environment to append the property to
     */
    private void appendProperty(CustomProperty property, ConfigurableEnvironment environment) {
        var result = new MapPropertySource(property.getPropertySourceName(), Map.of(property.getPropertyName(), property.getPropertyValue(environment)));
        // Order of overrides (last-named wins) and order of property source (packaged application.yml -> application.yml next to JAR -> system environment)
        // Using SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, this injects directly after system environment vars (highest priority).
        // this is required to also allow properties to be passed as env-vars instead of only via application.yml-files
        environment.getPropertySources().addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, result);
    }
}
