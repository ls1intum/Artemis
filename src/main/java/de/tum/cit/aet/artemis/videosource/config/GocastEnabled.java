package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GocastEnabled implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isEnabled(context.getEnvironment());
    }

    static boolean isEnabled(Environment environment) {
        return environment.getProperty("artemis.tum-live.integration-enabled", Boolean.class, false) && hasText(environment.getProperty("artemis.tum-live.api-base-url"))
                && hasText(environment.getProperty("artemis.tum-live.web-base-url")) && hasText(environment.getProperty("artemis.tum-live.api-key"))
                && hasText(environment.getProperty("server.url"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
