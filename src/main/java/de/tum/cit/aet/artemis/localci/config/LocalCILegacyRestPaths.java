package de.tum.cit.aet.artemis.localci.config;

/**
 * Centralised legacy URL prefix constants for the localci module. Each constant identifies a path
 * that the localci module still serves alongside its canonical {@code /api/localci/...} counterpart
 * so deployed clients keep working through the migration window.
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
public final class LocalCILegacyRestPaths {

    /**
     * Legacy class-level prefix from when the localci controllers (build plan, queue, log, phases)
     * lived in the programming module. Successor: {@code "api/localci/"}.
     */
    @Deprecated(forRemoval = true, since = "8.5")
    public static final String PROGRAMMING_PREFIX = "api/programming/";

    /**
     * Legacy class-level prefix from when {@link de.tum.cit.aet.artemis.localci.web.open.PublicBuildPlanResource}
     * lived in the programming module. Successor: {@code "api/localci/public/"}.
     */
    @Deprecated(forRemoval = true, since = "8.5")
    public static final String PROGRAMMING_PUBLIC_PREFIX = "api/programming/public/";

    /**
     * Legacy class-level prefix for {@link de.tum.cit.aet.artemis.localci.web.BuildPhasesTemplateResource}
     * which previously lived under {@code api/programming/phases/}. Successor: {@code "api/localci/phases/"}.
     */
    @Deprecated(forRemoval = true, since = "8.5")
    public static final String PROGRAMMING_PHASES_PREFIX = "api/programming/phases/";

    private LocalCILegacyRestPaths() {
        // utility class
    }
}
