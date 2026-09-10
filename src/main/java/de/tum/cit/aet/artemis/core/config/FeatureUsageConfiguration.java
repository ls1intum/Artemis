package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Enables {@link FeatureUsageProperties} binding for the built-in feature usage analysis.
 */
@Configuration
@Lazy
@Profile(PROFILE_CORE)
@EnableConfigurationProperties(FeatureUsageProperties.class)
public class FeatureUsageConfiguration {
}
