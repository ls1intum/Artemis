package de.tum.cit.aet.artemis.programming.service.sharing;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Sharing feature (for the Programming Exercise Sharing Platform) is enabled.
 * Based on this condition, Spring components concerning Sharing functionality can be enabled or disabled.
 */
public class SharingEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public SharingEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isSharingEnabled(context.getEnvironment());
    }
}
