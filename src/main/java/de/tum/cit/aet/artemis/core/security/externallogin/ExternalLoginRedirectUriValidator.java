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
 * are always rejected, which prevents classic open-redirect token theft. Optionally restricts the
 * callback authority (the host part, e.g. the extension id) to a configured allowlist.
 */
public class ExternalLoginRedirectUriValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("http", "https");

    static final int MAX_URI_BYTES = 512;

    private final List<String> allowedSchemes;

    private final List<String> allowedAuthorities;

    /**
     * @param allowedSchemes     the allowed custom URI schemes (lower-cased internally)
     * @param allowedAuthorities the optional allowlist of callback authorities (empty = any)
     */
    public ExternalLoginRedirectUriValidator(List<String> allowedSchemes, List<String> allowedAuthorities) {
        this.allowedSchemes = allowedSchemes.stream().map(scheme -> scheme.toLowerCase(Locale.ROOT)).toList();
        this.allowedAuthorities = allowedAuthorities.stream().map(authority -> authority.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * @return {@code true} if at least one scheme is allowlisted (i.e. the feature is enabled)
     */
    public boolean isFeatureEnabled() {
        return !allowedSchemes.isEmpty();
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

        if (!allowedAuthorities.isEmpty()) {
            String host = uri.getHost();
            if (host == null || !allowedAuthorities.contains(host.toLowerCase(Locale.ROOT))) {
                return Optional.of("callback authority is not in the allowlist");
            }
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
