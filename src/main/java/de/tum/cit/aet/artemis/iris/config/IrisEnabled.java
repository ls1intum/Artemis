package de.tum.cit.aet.artemis.iris.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Iris module is enabled.
 * Based on this condition, Spring components concerning Iris / Pyris functionality can be enabled or disabled.
 */
public class IrisEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public IrisEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isIrisEnabled(context.getEnvironment());
    }
}
