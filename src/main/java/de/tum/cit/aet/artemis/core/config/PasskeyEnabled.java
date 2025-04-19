package de.tum.cit.aet.artemis.core.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if the Passkey feature is enabled.
 * Based on this condition, Spring components concerning Passkey functionality can be enabled or disabled.
 */
public class PasskeyEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public PasskeyEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isPasskeyEnabled(context.getEnvironment());
    }
}
