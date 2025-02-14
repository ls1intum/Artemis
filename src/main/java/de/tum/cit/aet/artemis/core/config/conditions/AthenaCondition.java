package de.tum.cit.aet.artemis.core.config.conditions;

import static de.tum.cit.aet.artemis.core.config.conditions.ConditionHelper.isAthenaEnabled;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AthenaCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isAthenaEnabled(context.getEnvironment());
    }
}
