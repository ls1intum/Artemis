package de.tum.cit.aet.artemis.core.exception.failureAnalyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

import de.tum.cit.aet.artemis.core.exception.ConflictingPasskeyConfigurationException;

/**
 * FailureAnalyzer that provides helpful error messages when the passkey configuration is invalid.
 * This analyzer catches {@link ConflictingPasskeyConfigurationException} and formats it into a user-friendly message
 * with both a description of the problem and recommended actions to fix it.
 */
public class ConflictingPasskeyConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<ConflictingPasskeyConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ConflictingPasskeyConfigurationException cause) {
        String description = buildDescription(cause);
        String action = buildAction(cause);
        return new FailureAnalysis(description, action, cause);
    }

    private String buildDescription(ConflictingPasskeyConfigurationException cause) {
        return String.format(
                "Invalid passkey configuration detected:%n%n" + "Property '%s' is set to 'true', but property '%s' is set to 'false'.%n%n"
                        + "This configuration is invalid because passkey authentication must be enabled " + "if it is required for administrator features.",
                cause.getPropertyName(), cause.getConflictingPropertyName());
    }

    private String buildAction(ConflictingPasskeyConfigurationException cause) {
        return String.format(
                "Update your application configuration (e.g., application-core.yml or environment variables):%n%n" + "Option 1: Enable passkey authentication:%n"
                        + "    %s: true%n%n" + "Option 2: Disable the requirement for administrator features:%n" + "    %s: false%n%n",
                cause.getConflictingPropertyName(), cause.getPropertyName());
    }
}
