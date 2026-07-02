package de.tum.cit.aet.artemis.math.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the math module is enabled.
 * Based on this condition, Spring components concerning math exercise functionality can be enabled or disabled.
 */
public class MathEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public MathEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isMathEnabled(context.getEnvironment());
    }
}
