package de.tum.cit.aet.artemis.text.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the text module is enabled.
 * Based on this condition, Spring components concerning text functionality can be enabled or disabled.
 */
public class TextEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public TextEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isTextExerciseEnabled(context.getEnvironment());
    }
}
