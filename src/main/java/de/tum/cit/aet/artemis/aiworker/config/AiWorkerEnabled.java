package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables coordinator-side worker infrastructure only on explicitly configured core nodes. */
public class AiWorkerEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("artemis.aiworker.enabled", Boolean.class, false) && context.getEnvironment().matchesProfiles(PROFILE_CORE);
    }
}
