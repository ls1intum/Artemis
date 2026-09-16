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

    /**
     * Legacy class-level prefix used by AdminCourseResource. Still called by artemis-android, which
     * creates courses through {@code POST api/core/admin/courses}. Successor: {@code "api/admin/"}.
     * Removable once artemis-android#694 has shipped; every other admin resource has already dropped
     * this alias.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_PREFIX = "api/core/admin/";

    private LegacyAdminRestPaths() {
        // utility class
    }
}
