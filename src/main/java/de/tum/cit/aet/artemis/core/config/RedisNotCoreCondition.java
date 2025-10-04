package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RedisNotCoreCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
        String dataStoreConfig = context.getEnvironment().getProperty("artemis.continuous-integration.data-store", HAZELCAST);

        return dataStoreConfig.equalsIgnoreCase(REDIS) && !activeProfiles.contains(PROFILE_CORE);
    }
}
