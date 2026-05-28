package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tags responses served under the legacy {@code /api/communication/...} prefix that are handled by a
 * notification module controller with the standard deprecation signal headers:
 * <ul>
 * <li>{@code Deprecation: true} (RFC 9745) — marks the response as having been served from a deprecated path.</li>
 * <li>{@code Sunset: <date>} (RFC 8594) — the date after which the legacy path will stop responding.</li>
 * <li>{@code Link: <successor>; rel="successor-version"} (RFC 8288) — points clients at the replacement URL
 * under the {@code /api/notification/...} prefix.</li>
 * </ul>
 * The interceptor is intentionally a no-op for any other handler so it cannot affect endpoints owned by
 * the communication module that happen to share the {@code /api/communication/} prefix.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class LegacyNotificationPathDeprecationInterceptor implements HandlerInterceptor {

    static final String LEGACY_PREFIX = "/api/communication/";

    static final String SUCCESSOR_PREFIX = "/api/notification/";

    static final String NOTIFICATION_PACKAGE = "de.tum.cit.aet.artemis.notification";

    // IMF-fixdate per RFC 7231 §7.1.1.1. Update when the legacy prefix is actually removed.
    static final String SUNSET_DATE = "Wed, 30 Sep 2026 00:00:00 GMT";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (!handlerMethod.getBeanType().getPackageName().startsWith(NOTIFICATION_PACKAGE)) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(LEGACY_PREFIX)) {
            return true;
        }

        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET_DATE);
        String successor = SUCCESSOR_PREFIX + path.substring(LEGACY_PREFIX.length());
        response.setHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
        return true;
    }
}
