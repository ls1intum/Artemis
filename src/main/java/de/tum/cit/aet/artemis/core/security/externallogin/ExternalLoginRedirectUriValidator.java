package de.tum.cit.aet.artemis.core.security.externallogin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Validates extension callback URIs for the external-client browser login flow.
 * <p>
 * Only custom URI schemes in the configured allowlist are accepted; {@code http} and {@code https}
 * are always rejected, which prevents classic open-redirect token theft. The callback authority (the
 * host part, e.g. the extension id) must also match a configured allowlist: a non-empty authority
 * allowlist is <strong>required</strong> for the feature to be active, so that an allowed scheme on
 * its own can never deliver a JWT to an arbitrary handler.
 */
public class ExternalLoginRedirectUriValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("http", "https");

    static final int MAX_URI_BYTES = 512;

    private final List<String> allowedSchemes;

    private final List<String> allowedAuthorities;

    /**
     * @param allowedSchemes     the allowed custom URI schemes (lower-cased internally)
     * @param allowedAuthorities the allowlist of callback authorities (lower-cased internally); required (non-empty) for the feature to be active
     */
    public ExternalLoginRedirectUriValidator(List<String> allowedSchemes, List<String> allowedAuthorities) {
        this.allowedSchemes = allowedSchemes.stream().map(scheme -> scheme.toLowerCase(Locale.ROOT)).toList();
        this.allowedAuthorities = allowedAuthorities.stream().map(authority -> authority.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * The feature requires both a scheme and an authority allowlist: an allowed scheme without an authority allowlist
     * would trust every handler for that scheme, so such a (misconfigured) setup is treated as disabled.
     *
     * @return {@code true} if at least one scheme and at least one authority are allowlisted (i.e. the feature is enabled)
     */
    public boolean isFeatureEnabled() {
        return !allowedSchemes.isEmpty() && !allowedAuthorities.isEmpty();
    }

    /**
     * Validates a callback URI against the configured allowlist and security rules.
     *
     * @param callback the callback URI to validate
     * @return empty if valid, or a short rejection reason
     */
    public Optional<String> validate(String callback) {
        if (callback == null || callback.isBlank()) {
            return Optional.of("callback is empty");
        }
        if (callback.getBytes(StandardCharsets.UTF_8).length > MAX_URI_BYTES) {
            return Optional.of("callback exceeds maximum length of " + MAX_URI_BYTES + " bytes");
        }

        URI uri;
        try {
            uri = URI.create(callback);
        }
        catch (IllegalArgumentException e) {
            return Optional.of("callback is not a valid URI: " + e.getMessage());
        }

        if (!uri.isAbsolute()) {
            return Optional.of("callback must be an absolute URI");
        }

        // Opaque URIs (e.g. "vscode:callback" without "//") cannot reliably carry query parameters.
        if (uri.isOpaque()) {
            return Optional.of("callback must be a hierarchical URI");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (BLOCKED_SCHEMES.contains(scheme)) {
            return Optional.of("http/https callbacks are not allowed");
        }
        if (!allowedSchemes.contains(scheme)) {
            return Optional.of("URI scheme '" + scheme + "' is not in the allowlist");
        }

        // The authority allowlist is mandatory: an empty allowlist rejects every callback (fail closed), so an allowed
        // scheme alone can never deliver the one-time code (and the JWT it unlocks) to an arbitrary handler.
        String host = uri.getHost();
        if (host == null || !allowedAuthorities.contains(host.toLowerCase(Locale.ROOT))) {
            return Optional.of("callback authority is not in the allowlist");
        }

        if (uri.getUserInfo() != null) {
            return Optional.of("callback must not contain user info");
        }
        if (uri.getFragment() != null) {
            return Optional.of("callback must not contain a fragment");
        }

        return Optional.empty();
    }
}
