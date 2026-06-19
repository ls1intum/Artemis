package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if the gocast (TUM Live) service-account integration is enabled.
 * <p>
 * The integration is considered enabled when BOTH of the following properties are set to non-blank values:
 * <ul>
 * <li>{@code artemis.tum-live.api-base-url} — the gocast REST API base URL</li>
 * <li>{@code artemis.tum-live.service-account-token} — the long-lived bearer token for the service account</li>
 * </ul>
 * <p>
 * This condition gates components that use gocast's authenticated integration endpoints (EP1–EP7).
 * The existing public resolver ({@link TumLiveEnabled}) requires only {@code api-base-url} and is unaffected.
 */
public class GocastEnabled implements Condition {

    private static final String TUM_LIVE_API_BASE_URL_PROPERTY = "artemis.tum-live.api-base-url";

    private static final String TUM_LIVE_SERVICE_ACCOUNT_TOKEN_PROPERTY = "artemis.tum-live.service-account-token";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty(TUM_LIVE_API_BASE_URL_PROPERTY);
        String token = context.getEnvironment().getProperty(TUM_LIVE_SERVICE_ACCOUNT_TOKEN_PROPERTY);
        return url != null && !url.isBlank() && token != null && !token.isBlank();
    }
}
