package de.tum.cit.aet.artemis.lecture.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the lecture module is enabled.
 * Based on this condition, Spring components concerning lecture functionality can be enabled or disabled.
 */
public class LectureEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public LectureEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isLectureEnabled(context.getEnvironment());
    }
}
