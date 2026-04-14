package de.tum.cit.aet.artemis.core.security.saml2;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Validates redirect URIs for the SAML2 external client authentication flow.
 * <p>
 * Only custom URI schemes configured in the allowlist are accepted.
 * {@code http} and {@code https} are always rejected regardless of configuration.
 */
public class SAML2RedirectUriValidator {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("http", "https");

    private static final int MAX_URI_BYTES = 200;

    private final List<String> allowedSchemes;

    public SAML2RedirectUriValidator(List<String> allowedSchemes) {
        this.allowedSchemes = allowedSchemes.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    public boolean isFeatureEnabled() {
        return !allowedSchemes.isEmpty();
    }

    /**
     * Validates a redirect URI against the configured allowlist and security rules.
     *
     * @param redirectUri the redirect URI to validate
     * @return empty if valid, or a rejection reason string
     */
    public Optional<String> validate(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return Optional.of("redirect_uri is empty");
        }

        if (redirectUri.getBytes(StandardCharsets.UTF_8).length > MAX_URI_BYTES) {
            return Optional.of("redirect_uri exceeds maximum length of " + MAX_URI_BYTES + " bytes");
        }

        URI uri;
        try {
            uri = URI.create(redirectUri);
        }
        catch (IllegalArgumentException e) {
            return Optional.of("redirect_uri is not a valid URI: " + e.getMessage());
        }

        if (!uri.isAbsolute()) {
            return Optional.of("redirect_uri must be an absolute URI");
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        if (BLOCKED_SCHEMES.contains(scheme)) {
            return Optional.of("http/https redirect URIs are not allowed");
        }

        if (!allowedSchemes.contains(scheme)) {
            return Optional.of("URI scheme '" + scheme + "' is not in the allowlist");
        }

        if (uri.getFragment() != null) {
            return Optional.of("redirect_uri must not contain a fragment");
        }

        return Optional.empty();
    }
}
