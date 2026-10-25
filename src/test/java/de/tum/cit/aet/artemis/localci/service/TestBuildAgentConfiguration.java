package de.tum.cit.aet.artemis.localci.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
     * Installs spy answers after Spring creates the spy, before it is injected into any consumer. Programmatically scheduled tasks can start in a consumer's
     * {@code PostConstruct}, so waiting for all singletons to exist is too late. This ordinary processor runs after Spring's priority-ordered Mockito wrapper.
     *
     * @param overrides the lazily resolved holder, to avoid creating application beans while post-processors are registered
     * @return the processor that installs the stubs before the configuration is exposed
     */
    @Bean
    public static BeanPostProcessor installBuildAgentConfigurationOverrides(ObjectProvider<BuildAgentConfigurationOverrides> overrides) {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof BuildAgentConfiguration configuration) {
                    overrides.getObject().installOn(configuration);
                }
                return bean;
            }
        };
    }
}
