package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(name = "artemis.core.enabled", havingValue = "false")
@EnableAutoConfiguration(exclude = MetricsAutoConfiguration.class)
public class ConditionalMetricsExclusionConfiguration {
}
