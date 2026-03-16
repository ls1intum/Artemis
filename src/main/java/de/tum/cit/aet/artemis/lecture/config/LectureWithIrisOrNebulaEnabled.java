package de.tum.cit.aet.artemis.lecture.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the lecture module is enabled AND either Iris or Nebula is enabled.
 * Based on this condition, Spring components that require lecture functionality together with
 * either Iris or Nebula can be enabled.
 */
public class LectureWithIrisOrNebulaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public LectureWithIrisOrNebulaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean irisEnabled = artemisConfigHelper.isIrisEnabled(context.getEnvironment());
        boolean nebulaEnabled = artemisConfigHelper.isNebulaEnabled(context.getEnvironment());
        boolean lectureEnabled = artemisConfigHelper.isLectureEnabled(context.getEnvironment());

        return lectureEnabled && (irisEnabled || nebulaEnabled);
    }
}
