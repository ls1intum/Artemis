package de.tum.cit.aet.artemis.programming.theia;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Theia module is enabled.
 * Based on this condition, Spring components concerning Theia functionality can be enabled or disabled.
 */
public class TheiaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public TheiaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isTheiaEnabled(context.getEnvironment());
    }
}
