package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Binds the LLM preset assignments of Hyperion's authoring features on core nodes with Hyperion enabled.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@Configuration
@EnableConfigurationProperties(HyperionLlmPresets.class)
public class HyperionLlmConfiguration {
}
