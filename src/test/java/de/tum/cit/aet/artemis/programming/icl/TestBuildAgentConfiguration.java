package de.tum.cit.aet.artemis.programming.icl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.core.util.FixMissingServletPathProcessor;

/**
 * This class is used to overwrite the configuration of the local CI system ({@link BuildAgentConfiguration}).
 * In particular, it provides a DockerClient Bean that has all methods used in the tests mocked.
 */
@TestConfiguration
@Import(BuildAgentConfiguration.class) // Fall back to the default configuration if no overwrite is provided here.
public class TestBuildAgentConfiguration {

    @Bean
    public FixMissingServletPathProcessor fixMissingServletPathProcessor() {
        return new FixMissingServletPathProcessor();
    }
}
