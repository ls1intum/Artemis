package de.tum.cit.aet.artemis.sandbox.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the sandbox module is enabled.
 * Every Spring component of the module carries it, so a disabled module creates no beans and no endpoints, and no other module depends on it.
 */
public class SandboxEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public SandboxEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isSandboxEnabled(context.getEnvironment());
    }
}
