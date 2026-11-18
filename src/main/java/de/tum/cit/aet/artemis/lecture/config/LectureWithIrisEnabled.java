package de.tum.cit.aet.artemis.lecture.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if both the lecture module and the Iris module are enabled.
 * Used for beans that depend on the lecture content processing pipeline backed by Iris/Pyris.
 */
public class LectureWithIrisEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public LectureWithIrisEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isLectureEnabled(context.getEnvironment()) && artemisConfigHelper.isIrisEnabled(context.getEnvironment());
    }
}
