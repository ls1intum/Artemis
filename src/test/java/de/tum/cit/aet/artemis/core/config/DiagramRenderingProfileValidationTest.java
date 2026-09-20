package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;

/**
 * Tests the diagram rendering profile check of {@link ConfigurationValidator}.
 * <p>
 * Separate from {@link ConfigurationValidatorTest} and {@link Isolated}, because the profile is JVM-wide state that the
 * diagram library resolves once and then caches: these tests have to set it and reset the cached value, and doing that
 * while other tests render diagrams in the same JVM would make either side flaky.
 */
@Isolated
class DiagramRenderingProfileValidationTest {

    private static final String PLANTUML_SECURITY_PROFILE = "PLANTUML_SECURITY_PROFILE";

    private String originalProfile;

    @BeforeEach
    void clearProfile() {
        originalProfile = System.getProperty(PLANTUML_SECURITY_PROFILE);
        System.clearProperty(PLANTUML_SECURITY_PROFILE);
        forgetResolvedProfile();
    }

    @AfterEach
    void restoreProfile() {
        if (originalProfile == null) {
            System.clearProperty(PLANTUML_SECURITY_PROFILE);
        }
        else {
            System.setProperty(PLANTUML_SECURITY_PROFILE, originalProfile);
        }
        // Also reset here, so a permissive value set by a test cannot stay resolved for the rest of the JVM.
        forgetResolvedProfile();
    }

    /**
     * Drops the library's cached profile, so the next read resolves it again from the property. Without this, only the
     * first test in this class would exercise the value it configured.
     */
    private void forgetResolvedProfile() {
        ReflectionTestUtils.setField(SecurityUtils.class, "current", null);
    }

    private ConfigurationValidator createValidator() {
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getProperty(Constants.PASSKEY_ENABLED_PROPERTY_NAME, Boolean.class)).thenReturn(false);
        return new ConfigurationValidator(mockEnvironment, false, null, null, false, null, ConfigurationValidator.MIN_PORT, ConfigurationValidator.MIN_PORT, null, null, null, null,
                false, "http://localhost");
    }

    @Test
    void testUnconfiguredProfileIsPinnedToSandbox() {
        // The environment variable is the library's fallback and a process cannot unset its own environment, so this case
        // is only meaningful when the machine running the tests does not define it. The other cases set the system
        // property, which takes precedence, and are therefore unaffected.
        assumeThat(System.getenv(PLANTUML_SECURITY_PROFILE)).isNull();

        // The library's own fallback is permissive, so leaving the profile unset must not mean leaving it unrestricted.
        assertThatCode(createValidator()::validateConfigurations).doesNotThrowAnyException();

        assertThat(System.getProperty(PLANTUML_SECURITY_PROFILE)).isEqualTo(SecurityProfile.SANDBOX.name());
        assertThat(SecurityUtils.getSecurityProfile()).isEqualTo(SecurityProfile.SANDBOX);
    }

    @ParameterizedTest
    @ValueSource(strings = { "SANDBOX", "ALLOWLIST", "sandbox" })
    void testConfiguredRestrictiveProfileIsAcceptedAndKept(String configuredProfile) {
        // An operator may pick either restrictive profile; the library upper-cases the value, so a lower-case one counts.
        System.setProperty(PLANTUML_SECURITY_PROFILE, configuredProfile);

        assertThatCode(createValidator()::validateConfigurations).doesNotThrowAnyException();

        assertThat(System.getProperty(PLANTUML_SECURITY_PROFILE)).isEqualTo(configuredProfile);
    }

    @ParameterizedTest
    @ValueSource(strings = { "INTERNET", "INTERNET_WITH_DOTSVG", "INSECURE", "LEGACY", "sanbdox" })
    void testConfiguredPermissiveProfileIsRejected(String configuredProfile) {
        // The last value is a typo rather than a profile name: unrecognised values fall back to the permissive default, so
        // they have to fail exactly like an explicitly permissive one instead of quietly rendering without restrictions.
        System.setProperty(PLANTUML_SECURITY_PROFILE, configuredProfile);

        assertThatThrownBy(createValidator()::validateConfigurations).isInstanceOf(IllegalStateException.class).hasMessageContaining(PLANTUML_SECURITY_PROFILE);
    }
}
