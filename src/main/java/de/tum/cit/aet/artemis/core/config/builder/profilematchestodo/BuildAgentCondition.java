package de.tum.cit.aet.artemis.core.config.builder.profilematchestodo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.builder.PropertyConfigHelper;

public class BuildAgentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return PropertyConfigHelper.isBuildAgentEnabled(context.getEnvironment());
    }
}
