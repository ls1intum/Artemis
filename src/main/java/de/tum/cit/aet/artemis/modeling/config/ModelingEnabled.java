package de.tum.cit.aet.artemis.modeling.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the modeling module is enabled.
 * Based on this condition, Spring components concerning modeling functionality can be enabled or disabled.
 */
public class ModelingEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ModelingEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isModelingEnabled(context.getEnvironment());
    }
}
