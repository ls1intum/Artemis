package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Condition to check if the TUM Live integration is enabled.
 * The integration is considered enabled when the API base URL property is set to a non-blank value.
 * Worker-only nodes do not load this integration.
 * Based on this condition, Spring components concerning TUM Live functionality can be enabled or disabled.
 */
public class TumLiveEnabled implements Condition {

    private static final String TUM_LIVE_API_BASE_URL_PROPERTY = "artemis.tum-live.api-base-url";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (context.getEnvironment().matchesProfiles(Constants.PROFILE_AIWORKER + " & !" + Constants.PROFILE_CORE)) {
            return false;
        }
        String url = context.getEnvironment().getProperty(TUM_LIVE_API_BASE_URL_PROPERTY);
        return url != null && !url.isBlank();
    }
}
