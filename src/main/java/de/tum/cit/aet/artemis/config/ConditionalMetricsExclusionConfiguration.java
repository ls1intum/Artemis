package de.tum.cit.aet.artemis.config;

import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional configuration class that excludes {@link MetricsAutoConfiguration}
 * when the application is running with the 'buildagent' profile active and the 'core' profile inactive.
 * <p>
 * This configuration ensures that the MetricsAutoConfiguration is not applied in environments where
 * the application is being run by a build agent, which typically does not require metric collection,
 * and where the 'core' profile, which might need metrics, is not active.
 * </p>
 */
@Configuration
@Conditional(BuildAgentWithoutCoreCondition.class)
@EnableAutoConfiguration(exclude = MetricsAutoConfiguration.class)
public class ConditionalMetricsExclusionConfiguration {
}
