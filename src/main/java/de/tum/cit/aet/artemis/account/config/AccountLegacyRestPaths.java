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
 * Only the passkey prefix is left. The others were retired once their callers turned out to be the
 * web client, which ships with the server, or a developer script rather than a released app.
 * <p>
 * TODO: Remove this class together with all its references once external clients have migrated.
 */
public final class AccountLegacyRestPaths {

    /**
     * Legacy class-level prefix used by PasskeyResource. Still called by artemis-android, which reads
     * the current user's passkeys through {@code GET api/core/passkey/user}; the released 2.1.4 tag
     * does so from {@code PasskeySettingsServiceImpl}. Successor: {@code "api/account/passkeys/"}.
     * Removable once artemis-android#694 has shipped and the sunset in
     * {@code LegacyApiPathDeprecationInterceptor#SUNSET_DATE} has passed.
     */
    @Deprecated(forRemoval = true, since = "9.3")
    public static final String CORE_PASSKEY_PREFIX = "api/core/passkey/";

    private AccountLegacyRestPaths() {
        // utility class
    }
}
