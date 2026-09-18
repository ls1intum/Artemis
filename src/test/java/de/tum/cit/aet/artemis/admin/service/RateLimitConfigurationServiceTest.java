package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.RateLimitType;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigurationServiceTest {

    @Mock
    private RateLimitingProperties properties;

    private RateLimitConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        configurationService = new RateLimitConfigurationService(properties);
    }

    @Test
    void testIsRateLimitingEnabled_WhenEnabled_ShouldReturnTrue() {
        when(properties.isEnabled()).thenReturn(true);

        assertThat(configurationService.isRateLimitingEnabled()).isTrue();
    }

    @Test
    void testIsRateLimitingEnabled_WhenDisabled_ShouldReturnFalse() {
        when(properties.isEnabled()).thenReturn(false);

        assertThat(configurationService.isRateLimitingEnabled()).isFalse();
    }

    @Test
    void testGetEffectiveRpm_PublicType_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getAccountManagementRequestsPerMinute()).thenReturn(10);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT);

        assertThat(rpm).isEqualTo(10);
    }

    @Test
    void testGetEffectiveRpm_PublicType_WithNullValue_ShouldReturnDefault() {
        when(properties.getAccountManagementRequestsPerMinute()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.ACCOUNT_MANAGEMENT);

        assertThat(rpm).isEqualTo(RateLimitType.ACCOUNT_MANAGEMENT.getDefaultRpm()); // 5
    }

    @Test
    void testGetEffectiveRpm_LoginRelatedType_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getAuthenticationRequestsPerMinute()).thenReturn(50);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.AUTHENTICATION);

        assertThat(rpm).isEqualTo(50);
    }

    @Test
    void testGetEffectiveRpm_LoginRelatedType_WithNullValue_ShouldReturnDefault() {
        when(properties.getAuthenticationRequestsPerMinute()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.AUTHENTICATION);

        assertThat(rpm).isEqualTo(RateLimitType.AUTHENTICATION.getDefaultRpm()); // 30
    }

    @Test
    void testGetEffectiveRpm_LoginOptions_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getLoginOptionsRequestsPerMinute()).thenReturn(12);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.LOGIN_OPTIONS);

        assertThat(rpm).isEqualTo(12);
    }

    @Test
    void testGetEffectiveRpm_LoginOptions_WithNullValue_ShouldReturnDefault() {
        when(properties.getLoginOptionsRequestsPerMinute()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.LOGIN_OPTIONS);

        assertThat(rpm).isEqualTo(RateLimitType.LOGIN_OPTIONS.getDefaultRpm()); // 30
    }

    @Test
    void testGetEffectiveRpm_LoginOptions_DoesNotShareTheAuthenticationBudget() {
        // The two types must stay independently configurable: the login form calls login-options immediately before
        // authenticating, so a shared setting would silently halve the login budget.
        when(properties.getLoginOptionsRequestsPerMinute()).thenReturn(7);
        when(properties.getAuthenticationRequestsPerMinute()).thenReturn(99);

        assertThat(configurationService.getEffectiveRpm(RateLimitType.LOGIN_OPTIONS)).isEqualTo(7);
        assertThat(configurationService.getEffectiveRpm(RateLimitType.AUTHENTICATION)).isEqualTo(99);
    }

    @Test
    void testGetEffectiveRpm_ProblemStatementRendering_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getProblemStatementRenderingRequestsPerMinute()).thenReturn(42);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.PROBLEM_STATEMENT_RENDERING);

        assertThat(rpm).isEqualTo(42);
    }

    @Test
    void testGetEffectiveRpm_ProblemStatementRendering_WithNullValue_ShouldReturnDefault() {
        when(properties.getProblemStatementRenderingRequestsPerMinute()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.PROBLEM_STATEMENT_RENDERING);

        assertThat(rpm).isEqualTo(RateLimitType.PROBLEM_STATEMENT_RENDERING.getDefaultRpm()); // 30
    }

    @Test
    void testGetEffectiveRpm_RepositoryEditor_WithCustomValue_ShouldReturnCustomValue() {
        when(properties.getRepositoryEditorRequestsPerMinute()).thenReturn(200);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.REPOSITORY_EDITOR);

        assertThat(rpm).isEqualTo(200);
    }

    @Test
    void testGetEffectiveRpm_RepositoryEditor_WithNullValue_ShouldReturnDefault() {
        when(properties.getRepositoryEditorRequestsPerMinute()).thenReturn(null);

        int rpm = configurationService.getEffectiveRpm(RateLimitType.REPOSITORY_EDITOR);

        assertThat(rpm).isEqualTo(RateLimitType.REPOSITORY_EDITOR.getDefaultRpm()); // 120
    }
}
