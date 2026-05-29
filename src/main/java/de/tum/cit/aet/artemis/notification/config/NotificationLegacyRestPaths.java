package de.tum.cit.aet.artemis.notification.config;

/**
 * Centralised legacy URL prefix constants for the notification module. Each constant identifies a
 * path that the notification module still serves alongside its canonical {@code /api/notification/...}
 * counterpart so deployed clients keep working through the migration window.
 * <p>
 * All constants are annotated with {@link Deprecated @Deprecated(forRemoval = true)} on purpose: any
 * code referencing them will surface a compile-time deprecation warning, which makes the cleanup PR
 * a mechanical "remove every reference, then delete the constant" job. The warning is also a visible
 * signal in code review that the call site is intentionally on the legacy side of the migration.
 * <p>
 * TODO: Remove this class together with all its references (REST controllers, interceptor map) once
 * external clients (mobile apps, cached webapp bundles) have migrated. Target sunset: 2026-09-30 —
 * keep in sync with {@link LegacyNotificationPathDeprecationInterceptor#SUNSET_DATE}.
 */
public final class NotificationLegacyRestPaths {

    /**
     * Legacy class-level prefix from when authenticated notification endpoints lived in the
     * communication module. Successor: {@code "api/notification/"}.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String COMMUNICATION_PREFIX = "api/communication/";

    /**
     * Legacy class-level prefix used by the course-notification resources (CourseNotificationResource,
     * UserCourseNotificationSettingResource, UserCourseNotificationStatusResource). It folds in the
     * former {@code notification/} resource segment so the canonical paths drop the confusing
     * {@code api/notification/notification/...} duplication: the successor paths are now
     * {@code api/notification/...} (e.g. {@code api/notification/info},
     * {@code api/notification/{courseId}/settings}) while the legacy
     * {@code api/communication/notification/...} paths are unchanged.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String COMMUNICATION_NOTIFICATION_PREFIX = "api/communication/notification/";

    /**
     * Legacy class-level prefix from when {@code PublicSystemNotificationResource} lived in the core
     * module. The notification module's successor is {@code "api/notification/public/"}.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_PUBLIC_PREFIX = "api/core/public/";

    private NotificationLegacyRestPaths() {
        // utility class
    }
}
