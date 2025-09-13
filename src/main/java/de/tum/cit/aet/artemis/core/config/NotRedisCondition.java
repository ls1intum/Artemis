package de.tum.cit.aet.artemis.core.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class NotRedisCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dataStoreConfig = context.getEnvironment().getProperty("artemis.continuous-integration.data-store", "Hazelcast");
        return !dataStoreConfig.equalsIgnoreCase("Redis");
    }
}
