package de.tum.cit.aet.artemis.tutorialgroup.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the tutorialgroup module is enabled.
 * Based on this condition, Spring components concerning atlas functionality can be enabled or disabled.
 */
public class TutorialGroupEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public TutorialGroupEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isTutorialGroupEnabled(context.getEnvironment());
    }
}
