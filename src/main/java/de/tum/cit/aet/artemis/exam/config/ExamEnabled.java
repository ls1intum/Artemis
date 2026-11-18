package de.tum.cit.aet.artemis.exam.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Exam module is enabled.
 * Based on this condition, Spring components concerning exam functionality can be enabled or disabled.
 */
public class ExamEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public ExamEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isExamEnabled(context.getEnvironment());
    }
}
