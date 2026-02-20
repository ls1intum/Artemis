package de.tum.cit.aet.artemis.core.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if the CAMPUSOnline module is enabled.
 * Based on this condition, Spring components concerning CAMPUSOnline functionality can be enabled or disabled.
 */
public class CampusOnlineEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public CampusOnlineEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isCampusOnlineEnabled(context.getEnvironment());
    }
}
