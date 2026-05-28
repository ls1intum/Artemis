package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tags responses served under a legacy URL prefix that are handled by a notification module controller
 * with the standard deprecation signal headers:
 * <ul>
 * <li>{@code Deprecation: true} (RFC 9745) — marks the response as having been served from a deprecated path.</li>
 * <li>{@code Sunset: <date>} (RFC 8594) — the date after which the legacy path will stop responding.</li>
 * <li>{@code Link: <successor>; rel="successor-version"} (RFC 8288) — points clients at the replacement URL
 * under the new {@code /api/notification/...} prefix.</li>
 * </ul>
 * The package check on the resolved handler keeps the interceptor a no-op for any non-notification
 * controller, so endpoints owned by other modules sharing a legacy prefix (the communication module under
 * {@code /api/communication/}, the core module under {@code /api/core/public/}) cannot be affected.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class LegacyNotificationPathDeprecationInterceptor implements HandlerInterceptor {

    /**
     * Maps legacy URL prefixes that the notification module still serves for backwards compatibility to
     * their canonical replacements. Order does not matter — entries are disjoint by construction.
     * <p>
     * The keys are derived from {@link NotificationLegacyRestPaths} on purpose: that class owns every
     * legacy path constant in the module, and the {@link Deprecated @Deprecated} annotation on those
     * constants surfaces deprecation warnings everywhere they are consumed, so cleanup is mechanical.
     */
    @SuppressWarnings("deprecation")
    static final Map<String, String> LEGACY_TO_SUCCESSOR_PREFIX = Map.of(
            // Authenticated notification endpoints that used to live in the communication module.
            "/" + NotificationLegacyRestPaths.COMMUNICATION_PREFIX, "/api/notification/",
            // Unauthenticated public system-notification endpoint that used to live in the core module.
            "/" + NotificationLegacyRestPaths.CORE_PUBLIC_PREFIX + "system-notifications/", "/api/notification/public/system-notifications/");

    static final String NOTIFICATION_PACKAGE = "de.tum.cit.aet.artemis.notification";

    // RFC 9745 specifies the Deprecation header value as an IMF-fixdate (RFC 7231 §7.1.1.1).
    // This is the date the legacy prefixes were marked deprecated; align it with the commit that
    // introduced the deprecation. Plain `true` would be non-compliant with RFC 9745.
    static final String DEPRECATION_DATE = "Thu, 28 May 2026 00:00:00 GMT";

    // RFC 8594 — IMF-fixdate of the planned removal. Update when the legacy prefixes are actually removed.
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
        if (path == null) {
            return true;
        }
        for (Map.Entry<String, String> entry : LEGACY_TO_SUCCESSOR_PREFIX.entrySet()) {
            String legacyPrefix = entry.getKey();
            if (!path.startsWith(legacyPrefix)) {
                continue;
            }
            // Deprecation / Sunset are single-valued — setHeader is correct (no other writer sets them).
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Sunset", SUNSET_DATE);
            // Link is multi-valued (RFC 8288): use addHeader so we do not clobber a pagination Link header
            // that controllers like getAllSystemNotifications write via PaginationUtil.
            String successor = entry.getValue() + path.substring(legacyPrefix.length());
            response.addHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
            return true;
        }
        return true;
    }
}
