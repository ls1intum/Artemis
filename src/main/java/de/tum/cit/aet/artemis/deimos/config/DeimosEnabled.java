package de.tum.cit.aet.artemis.deimos.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Deimos module is enabled.
 * Based on this condition, Spring components concerning Deimos functionality can be enabled or disabled.
 */
public class DeimosEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public DeimosEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isDeimosEnabled(context.getEnvironment());
    }
}
