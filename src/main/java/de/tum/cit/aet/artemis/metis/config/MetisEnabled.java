package de.tum.cit.aet.artemis.metis.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Metis module is enabled.
 * Based on this condition, Spring components concerning Metis functionality can be enabled or disabled.
 */
public class MetisEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public MetisEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isMetisEnabled(context.getEnvironment());
    }
}
