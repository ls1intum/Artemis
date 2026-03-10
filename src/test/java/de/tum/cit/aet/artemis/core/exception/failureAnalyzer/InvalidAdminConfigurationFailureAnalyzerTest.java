package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.InvalidAdminConfigurationException;

/**
 * Test for {@link InvalidAdminConfigurationFailureAnalyzer}.
 */
class InvalidAdminConfigurationFailureAnalyzerTest {

    private final InvalidAdminConfigurationFailureAnalyzer analyzer = new InvalidAdminConfigurationFailureAnalyzer();

    @Test
    void testAnalyzeUsernameException() {
        InvalidAdminConfigurationException exception = new InvalidAdminConfigurationException("Internal admin username is too short", "username",
                "artemis.user-management.internal-admin.username", "ab", "must be at least 4 characters long");

        FailureAnalysis analysis = analyzer.analyze(exception, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Invalid internal admin configuration detected");
        assertThat(analysis.getDescription()).contains("artemis.user-management.internal-admin.username");
        assertThat(analysis.getDescription()).contains("must be at least 4 characters long");

        assertThat(analysis.getAction()).contains("Update your application configuration");
        assertThat(analysis.getAction()).contains("ARTEMIS_USER_MANAGEMENT_INTERNAL_ADMIN_USERNAME");
        assertThat(analysis.getAction()).contains("--artemis.user-management.internal-admin.username");
        assertThat(analysis.getAction()).contains("must be 4-50 characters long");
    }

    @Test
    void testAnalyzePasswordException() {
        InvalidAdminConfigurationException exception = new InvalidAdminConfigurationException("Internal admin password is too short", "password",
                "artemis.user-management.internal-admin.password", "***hidden***", "must be at least 8 characters long");

        FailureAnalysis analysis = analyzer.analyze(exception, exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("Invalid internal admin configuration detected");
        assertThat(analysis.getDescription()).contains("artemis.user-management.internal-admin.password");
        assertThat(analysis.getDescription()).contains("must be at least 8 characters long");

        assertThat(analysis.getAction()).contains("Update your application configuration");
        assertThat(analysis.getAction()).contains("ARTEMIS_USER_MANAGEMENT_INTERNAL_ADMIN_PASSWORD");
        assertThat(analysis.getAction()).contains("--artemis.user-management.internal-admin.password");
        assertThat(analysis.getAction()).contains("must be at least 8 characters long");
    }
}
