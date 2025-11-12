package de.tum.cit.aet.artemis.core.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * FailureAnalyzer that provides helpful error messages when the passkey configuration is invalid.
 * This analyzer catches {@link PasskeyConfigurationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class PasskeyConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<PasskeyConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, PasskeyConfigurationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(PasskeyConfigurationException cause) {
        return String.format(
                "Invalid passkey configuration detected:%n%n" + "Property '%s' is set to 'true', but property '%s' is set to 'false'.%n%n"
                        + "This configuration is invalid because passkey authentication must be enabled " + "if it is required for administrator features.",
                cause.getPropertyName(), cause.getConflictingPropertyName());
    }

    private String buildAction(PasskeyConfigurationException cause) {
        return String.format(
                "Update your application configuration (e.g., application-local.yml or environment variables):%n%n" + "Option 1: Enable passkey authentication:%n"
                        + "    %s: true%n%n" + "Option 2: Disable the requirement for administrator features:%n" + "    %s: false%n%n"
                        + "For more information about passkey configuration, please refer to the Artemis documentation.",
                cause.getConflictingPropertyName(), cause.getPropertyName());
    }
}
