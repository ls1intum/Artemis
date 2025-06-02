package de.tum.cit.aet.artemis.calendar.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the calendar feature is enabled.
 * Based on this condition, Spring components concerning calendar functionality can be enabled or disabled.
 */
public class CalendarEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public CalendarEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isCalendarEnabled(context.getEnvironment());
    }
}
