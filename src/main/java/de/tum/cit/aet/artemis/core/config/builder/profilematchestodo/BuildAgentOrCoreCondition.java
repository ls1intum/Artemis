package de.tum.cit.aet.artemis.core.config.builder.profilematchestodo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class BuildAgentOrCoreCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String artemisMode = context.getEnvironment().getProperty("artemis.mode", String.class);
        return "default".equals(artemisMode) || "buildagent-only".equals(artemisMode) || "core-only".equals(artemisMode);
    }
}
