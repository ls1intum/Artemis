package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GocastEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return environment.getProperty("artemis.tum-live.integration-enabled", Boolean.class, false) && hasText(environment.getProperty("artemis.tum-live.api-base-url"))
                && hasText(environment.getProperty("artemis.tum-live.web-base-url")) && hasText(environment.getProperty("artemis.tum-live.service-account-email"))
                && hasText(environment.getProperty("artemis.tum-live.service-account-password")) && hasText(environment.getProperty("server.url"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
