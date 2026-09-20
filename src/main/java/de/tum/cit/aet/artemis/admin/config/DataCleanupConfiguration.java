package de.tum.cit.aet.artemis.admin.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Enables {@link DataCleanupProperties} binding for the admin data-privacy cleanup jobs.
 */
@Configuration
@Lazy
@Profile(PROFILE_CORE)
@EnableConfigurationProperties(DataCleanupProperties.class)
public class DataCleanupConfiguration {
}
