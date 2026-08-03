package de.tum.cit.aet.artemis.admin.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

/**
 * Enables {@link AuditEventRetentionProperties} binding for the nightly audit log pruning.
 */
@Configuration
@Lazy
@Profile(PROFILE_CORE)
@EnableConfigurationProperties(AuditEventRetentionProperties.class)
public class AuditEventRetentionConfiguration {
}
