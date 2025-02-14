package de.tum.cit.aet.artemis.core.config.builder;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;
import static de.tum.cit.aet.artemis.core.config.builder.PropertyConfigHelper.isBuildAgentOnlyMode;
import static de.tum.cit.aet.artemis.core.config.builder.PropertyConfigHelper.isGitLabCIEnabled;
import static de.tum.cit.aet.artemis.core.config.builder.PropertyConfigHelper.isJenkinsEnabled;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
public class PropertyBuilder {

    private static final List<ConflictingOverrideProperty> properties = List.of(new AutoConfigurationExclusionProperty(), new LiquibaseEnabledProperty(),
            new CloudLoadBalancerCacheProperty(), new ManagementEndpointHealthDetailsProperty(), new EurekaClientEnabledProperty(),
            new ManagementEndpointJhiMetricsEnabledProperty(), new ManagementEndpointProbesEnabledProperty(), new ManagementMetricsEnableProperty());

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

            var activatedProperties = properties.stream().filter(s -> s.enabled(environment)).toList();

            for (ConflictingOverrideProperty property : activatedProperties) {
                appendProperty(property, environment);
            }
        });
    }

    private void validateConfig(ConfigurableEnvironment environment) {
        Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
        if (isBuildAgentOnlyMode(environment)) {
            if (activeProfiles.contains("gitlab") || activeProfiles.contains(PROFILE_JENKINS)) {
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
    private void appendProperty(ConflictingOverrideProperty property, ConfigurableEnvironment environment) {
        var result = new MapPropertySource(property.getPropertySourceName(), Map.of(property.getPropertyName(), property.getPropertyValue(environment)));
        environment.getPropertySources().addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, result);
    }
}
