package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration scoped to the notification module. Registers the
 * {@link LegacyNotificationPathDeprecationInterceptor} on the legacy URL prefix so it can decorate
 * responses with deprecation signal headers without affecting any other module.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class NotificationWebConfiguration implements WebMvcConfigurer {

    private final LegacyNotificationPathDeprecationInterceptor legacyNotificationPathDeprecationInterceptor;

    public NotificationWebConfiguration(LegacyNotificationPathDeprecationInterceptor legacyNotificationPathDeprecationInterceptor) {
        this.legacyNotificationPathDeprecationInterceptor = legacyNotificationPathDeprecationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(legacyNotificationPathDeprecationInterceptor).addPathPatterns(LegacyNotificationPathDeprecationInterceptor.LEGACY_PREFIX + "**");
    }
}
