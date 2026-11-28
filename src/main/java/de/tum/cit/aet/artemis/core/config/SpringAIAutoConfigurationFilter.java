package de.tum.cit.aet.artemis.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Limits Spring AI observation auto-configuration to nodes serving an AI workload. Registered in spring.factories. */
public class SpringAIAutoConfigurationFilter implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(SpringAIAutoConfigurationFilter.class);

    private Environment env;

    @Override
    public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata metadata) {
        // A standalone build agent can inherit the core feature flag; only the core or an explicit AI Worker workload needs the observation auto-configuration.
        boolean hyperionEnabled = env.acceptsProfiles(Profiles.of(Constants.PROFILE_CORE)) && env.getProperty(Constants.HYPERION_ENABLED_PROPERTY_NAME, Boolean.class, false);
        boolean atlasEnabled = env.getProperty(Constants.ATLAS_ENABLED_PROPERTY_NAME, Boolean.class, false);
        boolean hyperionWorker = env.acceptsProfiles(Profiles.of(Constants.PROFILE_AIWORKER)) && "hyperion-generation".equals(env.getProperty("artemis.aiworker.workload"));
        boolean springAIEnabled = env.acceptsProfiles(Profiles.of(Constants.PROFILE_AIWORKER)) ? hyperionWorker : hyperionEnabled || atlasEnabled;

        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int i = 0; i < autoConfigurationClasses.length; i++) {
            String fullyQualifiedClassName = autoConfigurationClasses[i];

            // autoConfigurationClasses can contain null values which leads to a NPE in the contains check below, so we handle it here
            if (fullyQualifiedClassName == null) {
                continue;
            }

            matches[i] = springAIEnabled || !fullyQualifiedClassName.startsWith("org.springframework.ai.");
            if (!matches[i]) {
                log.debug("Excluding auto-configuration {} because no AI workload is active", fullyQualifiedClassName);
            }
        }
        return matches;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }
}
