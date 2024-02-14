package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class CoreNotSchedulingCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());

        boolean isCoreActive = activeProfiles.contains(PROFILE_CORE);
        boolean isSchedulingActive = activeProfiles.contains("scheduling");
        return isCoreActive && !isSchedulingActive;
    }
}
