package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class LocalCiBuildAgentNotLocalOrRedisDataCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
        return (activeProfiles.contains(PROFILE_LOCALCI) || activeProfiles.contains(PROFILE_BUILDAGENT)) && !activeProfiles.contains("LOCAL_LOCALCI_DATA")
                && !activeProfiles.contains("redis");
    }
}
