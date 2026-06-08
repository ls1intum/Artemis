package de.tum.cit.aet.artemis.modeling.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if both the modeling module and the Apollon module are enabled.
 * Used for beans that bridge modeling exercises with the Apollon conversion service.
 */
public class ModelingWithApollonEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ModelingWithApollonEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isModelingEnabled(context.getEnvironment()) && artemisConfigHelper.isApollonEnabled(context.getEnvironment());
    }
}
