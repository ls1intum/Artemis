package de.tum.cit.aet.artemis.modeling.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Apollon module is enabled.
 * Based on this condition, Spring components concerning Apollon functionality can be enabled or disabled.
 */
public class ApollonEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ApollonEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isApollonEnabled(context.getEnvironment());
    }
}
