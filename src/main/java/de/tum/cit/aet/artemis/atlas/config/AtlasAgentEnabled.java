package de.tum.cit.aet.artemis.atlas.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Conditional annotation for Atlas Agent components.
 * Only loads when Atlas Agent is enabled in configuration.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "artemis.atlas.agent.enabled", havingValue = "true", matchIfMissing = false)
public @interface AtlasAgentEnabled {
}
