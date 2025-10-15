package de.tum.cit.aet.artemis.nebula.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Nebula module is enabled.
 * Based on this condition, Spring components concerning nebula functionality can be enabled or disabled.
 */
public class NebulaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public NebulaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isNebulaEnabled(context.getEnvironment());
    }
}
