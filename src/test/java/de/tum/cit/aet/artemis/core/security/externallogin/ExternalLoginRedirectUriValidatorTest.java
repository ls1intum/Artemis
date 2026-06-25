package de.tum.cit.aet.artemis.core.security.externallogin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExternalLoginRedirectUriValidatorTest {

    private static final List<String> SCHEMES = List.of("vscode", "vscode-insiders");

    private ExternalLoginRedirectUriValidator validator(List<String> schemes, List<String> authorities) {
        return new ExternalLoginRedirectUriValidator(schemes, authorities);
    }

    @Test
    void featureIsDisabledWhenNoSchemeIsAllowlisted() {
        assertThat(validator(List.of(), List.of()).isFeatureEnabled()).isFalse();
    }

    @Test
    void featureIsEnabledWhenAtLeastOneSchemeIsAllowlisted() {
        assertThat(validator(List.of("vscode"), List.of()).isFeatureEnabled()).isTrue();
    }

    @Test
    void acceptsAllowlistedCustomScheme() {
        assertThat(validator(SCHEMES, List.of()).validate("vscode://aet-tum.iris-thaumantias/callback")).isEmpty();
    }

    @Test
    void acceptsSchemeCaseInsensitively() {
        assertThat(validator(SCHEMES, List.of()).validate("VSCode://host/callback")).isEmpty();
    }

    @Test
    void rejectsNullOrBlankCallback() {
        assertThat(validator(SCHEMES, List.of()).validate(null)).isPresent();
        assertThat(validator(SCHEMES, List.of()).validate("   ")).isPresent();
    }

    @Test
    void rejectsHttpAndHttpsEvenIfSomehowAllowlisted() {
        var validator = validator(List.of("http", "https", "vscode"), List.of());
        assertThat(validator.validate("http://example.com/callback")).isPresent();
        assertThat(validator.validate("https://example.com/callback")).isPresent();
    }

    @Test
    void rejectsSchemeNotInAllowlist() {
        assertThat(validator(SCHEMES, List.of()).validate("evilapp://host/callback")).isPresent();
    }

    @Test
    void rejectsRelativeUri() {
        assertThat(validator(SCHEMES, List.of()).validate("not-absolute/callback")).isPresent();
    }

    @Test
    void rejectsOpaqueUri() {
        // "vscode:callback" is opaque (no "//"), which cannot reliably carry query parameters.
        assertThat(validator(SCHEMES, List.of()).validate("vscode:callback")).isPresent();
    }

    @Test
    void rejectsCallbackWithUserInfo() {
        assertThat(validator(SCHEMES, List.of()).validate("vscode://user:pass@host/callback")).isPresent();
    }

    @Test
    void rejectsCallbackWithFragment() {
        assertThat(validator(SCHEMES, List.of()).validate("vscode://host/callback#fragment")).isPresent();
    }

    @Test
    void rejectsCallbackExceedingMaxLength() {
        String longCallback = "vscode://host/callback?data=" + "a".repeat(ExternalLoginRedirectUriValidator.MAX_URI_BYTES);
        assertThat(validator(SCHEMES, List.of()).validate(longCallback)).isPresent();
    }

    @Test
    void acceptsAllowlistedAuthority() {
        assertThat(validator(SCHEMES, List.of("aet-tum.iris-thaumantias")).validate("vscode://aet-tum.iris-thaumantias/callback")).isEmpty();
    }

    @Test
    void acceptsAuthorityCaseInsensitively() {
        assertThat(validator(SCHEMES, List.of("aet-tum.iris-thaumantias")).validate("vscode://AET-TUM.Iris-Thaumantias/callback")).isEmpty();
    }

    @Test
    void rejectsAuthorityNotInAllowlist() {
        assertThat(validator(SCHEMES, List.of("aet-tum.iris-thaumantias")).validate("vscode://some-other-extension/callback")).isPresent();
    }
}
