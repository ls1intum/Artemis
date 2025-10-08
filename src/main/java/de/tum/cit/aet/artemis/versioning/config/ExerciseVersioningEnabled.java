package de.tum.cit.aet.artemis.versioning.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Exercise versioning module is enabled.
 * Based on this condition, Spring components concerning exercise versioning functionality can be enabled or disabled.
 */
public class ExerciseVersioningEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ExerciseVersioningEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isExerciseVersioningEnabled(context.getEnvironment());
    }
}
