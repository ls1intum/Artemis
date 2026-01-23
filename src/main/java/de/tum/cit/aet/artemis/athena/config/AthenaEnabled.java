package de.tum.cit.aet.artemis.athena.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Athena module is enabled.
 * Based on this condition, Spring components concerning Athena functionality can be enabled or disabled.
 */
public class AthenaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public AthenaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isAthenaEnabled(context.getEnvironment());
    }
}
