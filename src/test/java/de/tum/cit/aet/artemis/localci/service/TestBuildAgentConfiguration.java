package de.tum.cit.aet.artemis.localci.service;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.util.FixMissingServletPathProcessor;

/**
 * This class is used to overwrite the configuration of the local CI system ({@link BuildAgentConfiguration}).
 * In particular, it lets tests replace the DockerClient and the build executor through {@link BuildAgentConfigurationOverrides}.
 */
@TestConfiguration
@Import(BuildAgentConfiguration.class) // Fall back to the default configuration if no overwrite is provided here.
public class TestBuildAgentConfiguration {

    @Bean
    public FixMissingServletPathProcessor fixMissingServletPathProcessor() {
        return new FixMissingServletPathProcessor();
    }

    @Bean
    public BuildAgentConfigurationOverrides buildAgentConfigurationOverrides() {
        return new BuildAgentConfigurationOverrides();
    }

    /**
     * Stubs the {@link BuildAgentConfiguration} spy once, after all singletons exist and before the scheduled tasks start (they start when the context is refreshed).
     *
     * @param configuration the build agent configuration, a spy when the test declares one
     * @param overrides     the overrides the stubbed answers read
     * @return the callback that installs the stubs
     */
    @Bean
    public SmartInitializingSingleton installBuildAgentConfigurationOverrides(BuildAgentConfiguration configuration, BuildAgentConfigurationOverrides overrides) {
        return () -> overrides.installOn(configuration);
    }
}
