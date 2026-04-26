package de.tum.cit.aet.artemis.core.security.saml2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SAML2RedirectUriValidatorTest {

    private SAML2RedirectUriValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SAML2RedirectUriValidator(List.of("vscode", "artemis-ios"));
    }

    @Test
    void testValidVscodeUri() {
        assertThat(validator.validate("vscode://artemis/callback")).isEmpty();
    }

    @Test
    void testValidUriWithQueryParams() {
        assertThat(validator.validate("vscode://artemis/callback?state=abc")).isEmpty();
    }

    @Test
    void testRejectHttpScheme() {
        assertThat(validator.validate("http://evil.com/steal")).isPresent();
    }

    @Test
    void testRejectHttpsScheme() {
        assertThat(validator.validate("https://evil.com/steal")).isPresent();
    }

    @Test
    void testRejectUnknownScheme() {
        assertThat(validator.validate("evil-scheme://callback")).isPresent();
    }

    @Test
    void testRejectRelativeUri() {
        assertThat(validator.validate("/relative/path")).isPresent();
    }

    @Test
    void testRejectFragment() {
        assertThat(validator.validate("vscode://callback#fragment")).isPresent();
    }

    @Test
    void testRejectTooLong() {
        String longUri = "vscode://artemis/" + "a".repeat(200);
        assertThat(validator.validate(longUri)).isPresent();
    }

    @Test
    void testRejectMalformedUri() {
        assertThat(validator.validate("://not-a-uri")).isPresent();
    }

    @Test
    void testRejectEmptyString() {
        assertThat(validator.validate("")).isPresent();
    }

    @Test
    void testCaseInsensitiveScheme() {
        assertThat(validator.validate("VSCODE://artemis/callback")).isEmpty();
    }

    @Test
    void testHttpBlockedEvenIfInAllowlist() {
        var permissiveValidator = new SAML2RedirectUriValidator(List.of("http", "https", "vscode"));
        assertThat(permissiveValidator.validate("http://evil.com")).isPresent();
        assertThat(permissiveValidator.validate("https://evil.com")).isPresent();
        assertThat(permissiveValidator.validate("vscode://callback")).isEmpty();
    }

    @Test
    void testEmptyAllowlistRejectsEverything() {
        var emptyValidator = new SAML2RedirectUriValidator(List.of());
        assertThat(emptyValidator.validate("vscode://callback")).isPresent();
    }

    @Test
    void testFeatureDisabledCheck() {
        var emptyValidator = new SAML2RedirectUriValidator(List.of());
        assertThat(emptyValidator.isFeatureEnabled()).isFalse();

        assertThat(validator.isFeatureEnabled()).isTrue();
    }
}
