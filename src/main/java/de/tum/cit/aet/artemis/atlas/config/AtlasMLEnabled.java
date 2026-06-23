package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the AtlasML submodule is enabled.
 * Based on this condition, Spring components concerning atlas ml functionality can be enabled or disabled.
 */
public class AtlasMLEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public AtlasMLEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isAtlasMLEnabled(context.getEnvironment());
    }
}
