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
 * keep in sync with {@code LegacyApiPathDeprecationInterceptor#SUNSET_DATE}.
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
     * {@code api/notification/courses/...} (e.g. {@code api/notification/courses/info},
     * {@code api/notification/courses/{courseId}/settings}) while the legacy
     * {@code api/communication/notification/...} paths are unchanged.
     * <p>
     * Still called by artemis-android, which reads {@code GET api/communication/notification/info} and
     * the per-course settings and presets under the same prefix. Removable once artemis-android#694 has
     * shipped.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String COMMUNICATION_NOTIFICATION_PREFIX = "api/communication/notification/";

    private NotificationLegacyRestPaths() {
        // utility class
    }
}
