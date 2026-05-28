package de.tum.cit.aet.artemis.localvc.config;

/**
 * Centralised legacy URL prefix constants for the localvc module. Each constant identifies a path
 * that the localvc module still serves alongside its canonical {@code /api/localvc/...} counterpart
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
public final class LocalVCLegacyRestPaths {

    /**
     * Legacy class-level prefix from when {@link de.tum.cit.aet.artemis.localvc.web.ssh.SshFingerprintsProviderResource}
     * lived in the programming module. Successor: {@code "api/localvc/"}.
     */
    @Deprecated(forRemoval = true, since = "8.5")
    public static final String PROGRAMMING_PREFIX = "api/programming/";

    private LocalVCLegacyRestPaths() {
        // utility class
    }
}
