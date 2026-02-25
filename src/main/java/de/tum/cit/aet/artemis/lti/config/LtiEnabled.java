package de.tum.cit.aet.artemis.lti.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the LTI module is enabled.
 * Based on this condition, Spring components concerning LTI functionality can be enabled or disabled.
 */
public class LtiEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public LtiEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isLtiEnabled(context.getEnvironment());
    }
}
