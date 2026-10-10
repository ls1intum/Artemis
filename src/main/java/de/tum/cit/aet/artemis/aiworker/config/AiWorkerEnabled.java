package de.tum.cit.aet.artemis.aiworker.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/** Enables coordinator-side worker infrastructure for an active workload or an explicit standalone setting. */
public class AiWorkerEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return new ArtemisConfigHelper().isAiWorkerEnabled(context.getEnvironment());
    }
}
