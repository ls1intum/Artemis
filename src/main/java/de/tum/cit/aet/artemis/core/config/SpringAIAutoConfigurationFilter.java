package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * {@code SpringAIAutoConfigurationFilter} is a custom {@link AutoConfigurationImportFilter}
 * that conditionally excludes certain Spring AI Azure/OpenAI auto-configurations from being
 * loaded into the application context.
 * <p>
 * This filter checks whether Hyperion, Atlas, or the Hyperion AI Worker workload is enabled. If none is enabled,
 * all autoconfiguration fully qualified class names starting with org.springframework.ai will be
 * filtered out and not applied. Otherwise, the filter allows all
 * auto-configurations to proceed (it does not re-include anything excluded elsewhere such
 * as through {@code spring.autoconfigure.exclude} in YAML).
 * <p>
 * This prevents unnecessary model beans on nodes that do not use them.
 * <p>
 * Note that this filter only affects the specified classes. Other exclusions (e.g. via
 * {@code spring.autoconfigure.exclude} in {@code application.yml}) still apply independently.
 *
 * <h3>Registration</h3>
 * To activate this filter, it must be declared in
 * {@code src/main/resources/META-INF/spring.factories}
 */
public class SpringAIAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(SpringAIAutoConfigurationFilter.class);

    private Environment env;

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        // A standalone build agent can inherit the core feature flag; only the core or an explicit AI Worker workload needs the model auto-configuration.
        boolean hyperionEnabled = env.acceptsProfiles(Profiles.of(Constants.PROFILE_CORE)) && env.getProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, Boolean.class, false);
        boolean atlasEnabled = env.getProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, Boolean.class, false);
        boolean hyperionWorker = env.acceptsProfiles(Profiles.of("aiworker")) && "hyperion-generation".equals(env.getProperty("artemis.aiworker.workload"));
        boolean springAIEnabled = hyperionEnabled || atlasEnabled || hyperionWorker;

        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String fullyQualifiedClassName = autoConfigurationClasses[i];

            // autoConfigurationClasses can contain null values which leads to a NPE in the contains check below, so we handle it here
            if (fullyQualifiedClassName == null) {
                continue;
            }

            matches[i] = springAIEnabled || !fullyQualifiedClassName.startsWith("org.springframework.ai");
            if (!matches[i]) {
                log.debug("Excluding auto-configuration: {} because no AI workload is active", fullyQualifiedClassName);
            }
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
