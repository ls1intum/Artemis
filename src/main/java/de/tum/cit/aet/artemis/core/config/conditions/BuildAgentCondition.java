package de.tum.cit.aet.artemis.core.config.conditions;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class BuildAgentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ArtemisConfigHelper.isBuildAgentEnabled(context.getEnvironment());
    }
}
