package de.tum.cit.aet.artemis.admin.config;

/**
 * Centralised legacy URL prefix constants for the admin module. The {@code /api/core/admin/...}
 * paths are kept alongside the canonical {@code /api/admin/...} paths so deployed clients keep
 * working through the migration window. Picked up automatically by the generic
 * {@code LegacyApiPathDeprecationInterceptor} via the multi-path {@code @RequestMapping} convention.
 * <p>
 * TODO: Remove this class together with all its references once external clients have migrated.
 */
public final class LegacyAdminRestPaths {

    /** Legacy class-level prefix used by most admin resources. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_PREFIX = "api/core/admin/";

    /** Legacy class-level prefix used by AdminCleanupResource. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_CLEANUP_PREFIX = "api/core/admin/cleanup/";

    /** Legacy class-level prefix used by AdminMetricsResource. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_METRICS_PREFIX = "api/core/admin/metrics/";

    /** Legacy class-level prefix used by AdminWebsocketResource. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_WEBSOCKET_PREFIX = "api/core/admin/websocket/";

    private LegacyAdminRestPaths() {
        // utility class
    }
}
