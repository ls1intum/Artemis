package de.tum.cit.aet.artemis.account.config;

/**
 * Centralised legacy URL prefix constants for the account module. Every constant identifies a path
 * that the account module still serves alongside its canonical {@code /api/account/...} counterpart
 * so deployed clients keep working through the migration window.
 * <p>
 * All constants are annotated {@link Deprecated @Deprecated(forRemoval = true)} on purpose: any code
 * referencing them surfaces a compile-time deprecation warning, which makes the cleanup PR a
 * mechanical "remove every reference, then delete the constants" job. The dual-path mapping is
 * picked up automatically by {@code LegacyApiPathDeprecationInterceptor}, which tags every legacy-
 * prefix request with {@code Deprecation} / {@code Sunset} / {@code Link} headers.
 * <p>
 * TODO: Remove this class together with all its references once external clients have migrated.
 */
public final class AccountLegacyRestPaths {

    /** Legacy class-level prefix used by TokenResource and UserResource. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_PREFIX = "api/core/";

    /**
     * Legacy class-level prefix used by AccountResource. It folds in the former {@code account/}
     * resource segment so the canonical paths drop the confusing {@code api/account/account/...}
     * duplication: the successor paths are now {@code api/account/...} (e.g.
     * {@code api/account/profile-picture}, {@code api/account/basic-information}) while the legacy
     * {@code api/core/account/...} paths are unchanged.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ACCOUNT_PREFIX = "api/core/account/";

    /** Legacy class-level prefix used by AdminUserResource (admin user management). */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_ADMIN_PREFIX = "api/core/admin/";

    /** Legacy class-level prefix used by PasskeyResource. */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_PASSKEY_PREFIX = "api/core/passkey/";

    private AccountLegacyRestPaths() {
        // utility class
    }
}
