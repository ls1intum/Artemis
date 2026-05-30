package de.tum.cit.aet.artemis.course.config;

/**
 * Centralised legacy URL prefix constants for the course module. The legacy {@code /api/core/...}
 * paths are kept alongside the canonical {@code /api/course/...} paths so deployed clients keep
 * working through the migration window. Picked up automatically by the generic
 * {@code LegacyApiPathDeprecationInterceptor} via the multi-path {@code @RequestMapping} convention.
 * <p>
 * TODO: Remove this class together with all its references once external clients have migrated.
 */
public final class CourseLegacyRestPaths {

    /** Legacy class-level prefix shared by every course-module REST controller. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_PREFIX = "api/core/";

    private CourseLegacyRestPaths() {
        // utility class
    }
}
