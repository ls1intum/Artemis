package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.core.exception.ConflictingPasskeyConfigurationException;
import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;
import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;

@Isolated("Changes PlantUML JVM security properties during validation.")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ConfigurationValidatorBoundaryTest {

    private MockedStatic<SecurityUtils> diagramSecurity;

    private String originalProfile;

    @BeforeEach
    void setUp() {
        originalProfile = System.getProperty("PLANTUML_SECURITY_PROFILE");
        System.setProperty("PLANTUML_SECURITY_PROFILE", "SANDBOX");
        diagramSecurity = mockStatic(SecurityUtils.class);
        diagramSecurity.when(SecurityUtils::getSecurityProfile).thenReturn(SecurityProfile.SANDBOX);
    }

    @AfterEach
    void restoreProfile() {
        diagramSecurity.close();
        if (originalProfile == null) {
            System.clearProperty("PLANTUML_SECURITY_PROFILE");
        }
        else {
            System.setProperty("PLANTUML_SECURITY_PROFILE", originalProfile);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "http://localhost", "https://example.invalid/base" })
    void acceptsMissingOrValidAbsoluteServerUrl(String url) {
        assertThatCode(() -> validator(url, false, new MockEnvironment(), null, null).validateConfigurations()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "relative/path", "mailto:admin@example.invalid", "ftp://example.invalid", "http:///no-host", "https://", "http://[invalid" })
    void rejectsMalformedNonHttpOrHostlessServerUrl(String url) {
        assertThatThrownBy(() -> validator(url, false, new MockEnvironment(), null, null).validateConfigurations()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server.url");
    }

    @Test
    void administratorPasskeyRequirementFailsUnlessPasskeysAreEnabled() {
        var environment = new MockEnvironment().withProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, "false");
        assertThatThrownBy(() -> validator("https://example.invalid", true, environment, null, null).validateConfigurations())
                .isInstanceOf(ConflictingPasskeyConfigurationException.class).hasMessageContaining(Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME);
        environment.setProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, "true");
        assertThatCode(() -> validator("https://example.invalid", true, environment, null, null).validateConfigurations()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(value = { "admin,NULL,password", "NULL,valid-password,username", "ab,valid-password,too short", "admin,abc,too short" }, nullValues = "NULL")
    void invalidAdminCredentialsReportTheOffendingFieldWithoutPrintingSecrets(String username, String password, String reason) {
        assertThatThrownBy(() -> validator("https://example.invalid", false, new MockEnvironment(), username, password).validateConfigurations())
                .isInstanceOf(InvalidAdminConfigurationException.class).hasMessageContaining(reason).hasMessageNotContaining("valid-password");
    }

    @Test
    void adminNameUpperBoundaryAndEmptyConfigurationAreHandled() {
        String longName = "u".repeat(Constants.USERNAME_MAX_LENGTH + 1);
        assertThatThrownBy(() -> validator("https://example.invalid", false, new MockEnvironment(), longName, "valid-password").validateConfigurations())
                .isInstanceOf(InvalidAdminConfigurationException.class).hasMessageContaining("too long");
        assertThatCode(() -> validator("https://example.invalid", false, new MockEnvironment(), " ", " ").validateConfigurations()).doesNotThrowAnyException();
        assertThatCode(
                () -> validator("https://example.invalid", false, new MockEnvironment(), "u".repeat(Constants.USERNAME_MAX_LENGTH), "valid-password").validateConfigurations())
                .doesNotThrowAnyException();
    }

    private ConfigurationValidator validator(String serverUrl, boolean requirePasskey, MockEnvironment environment, String username, String password) {
        if (!environment.containsProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME)) {
            environment.setProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, "false");
        }
        if (!environment.containsProperty(Constants.DEIMOS_ENABLED_PROPERTY_NAME)) {
            environment.setProperty(Constants.DEIMOS_ENABLED_PROPERTY_NAME, "false");
        }
        return new ConfigurationValidator(environment, requirePasskey, username, password, false, null, 8080, 50051, null, null, null, null, false, serverUrl, "", "",
                "/api/chat/completions", 90, 3);
    }
}
