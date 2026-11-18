package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Hyperion module is enabled.
 * Based on this condition, Spring components concerning hyperion functionality can be enabled or disabled.
 */
public class HyperionEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public HyperionEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isHyperionEnabled(context.getEnvironment());
    }
}
