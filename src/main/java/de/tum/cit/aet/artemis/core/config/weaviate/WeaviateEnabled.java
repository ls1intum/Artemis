package de.tum.cit.aet.artemis.core.config.weaviate;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if Weaviate integration is enabled.
 * Based on this condition, Spring components concerning Weaviate functionality can be enabled or disabled.
 */
public class WeaviateEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public WeaviateEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isWeaviateEnabled(context.getEnvironment());
    }
}
